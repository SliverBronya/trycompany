#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
田诊助手 大模型连通性自检

用法:
    set TZ_AI_API_KEY=xxx            (PowerShell: $env:TZ_AI_API_KEY="xxx")
    python scripts/check-ai.py
    python scripts/check-ai.py --image D:\\path\\to\\leaf.png

它**不经过后端**，直接按后端内部一模一样的请求形态打服务商接口，用来在重启整个
系统之前先回答三个问题：

  1. key 能不能用、模型 id 存不存在（换服务商最容易错的就是模型名）；
  2. 图片以 base64 data URL 内联发过去，服务商收不收（glm-4v-flash 就不收）；
  3. 模型肯不肯只吐一个 JSON 对象（不裹 markdown 围栏、不写多余的话）。
     这条是后面 DosageGuard 能不能生效的前提 —— 解析不出 JSON 就没有结构化字段可校验。

第 3 条不达标不代表链路坏了（后端会降级到知识库路径），但说明该换个模型。

注意：脚本默认拿演示图做连通性验证，而演示图是**印着「示意图」字样的程序生成图**，
模型大概率会说「这不是真实叶片」。**这属于预期结果**，本脚本不检验识别准确率，
只看请求能不能通、返回能不能解析。

只用标准库，不依赖 requests。
"""

import argparse
import base64
import glob
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC_YML = os.path.join(ROOT, "backend", "ruoyi-admin", "src", "main", "resources", "application.yml")
JAR = os.path.join(ROOT, "backend", "ruoyi-admin", "target", "ruoyi-admin.jar")
UPLOAD_DIR = os.path.join("D:/ruoyi/uploadPath/upload")

KEYS = ("provider", "base-url", "vision-provider", "vision-base-url",
        "vision-model", "text-provider", "text-base-url", "text-model", "temperature")


def read_yml_text(path):
    with open(path, "rb") as f:
        return f.read().decode("utf-8")


def pick(yml_text, key, default=None):
    """从 application.yml 的 tz.ai 段里抠一个值。不引 yaml 依赖，够用就行。"""
    m = re.search(r"^\s+%s:\s*(.+?)\s*$" % re.escape(key), yml_text, re.M)
    if not m:
        return default
    return m.group(1).strip().strip('"').strip("'")


def load_config():
    cfg = {}
    src = read_yml_text(SRC_YML)
    for k in KEYS:
        cfg[k] = pick(src, k)

    # 源码改完没重新打包是很常见的一步 —— 那时跑起来的是 jar 里的旧配置，
    # 而现象是「明明改了 provider 却还报旧服务商的错」。这里直接对一遍。
    cfg["_jar_diff"] = []
    if os.path.isfile(JAR):
        try:
            with zipfile.ZipFile(JAR) as z:
                jar_yml = z.read("BOOT-INF/classes/application.yml").decode("utf-8")
            for k in KEYS:
                if pick(jar_yml, k) != cfg[k]:
                    cfg["_jar_diff"].append((k, pick(jar_yml, k), cfg[k]))
        except Exception as e:  # noqa: BLE001 - 读不到 jar 不影响后续探测
            cfg["_jar_note"] = str(e)
    return cfg


def extract_json(text):
    """宽容解析：剥掉 ``` 围栏，取第一个 { 到最后一个 }。与后端 LlmJson 同思路。"""
    if not text:
        return None
    s = text.strip()
    s = re.sub(r"^```[a-zA-Z]*\s*", "", s)
    s = re.sub(r"\s*```$", "", s).strip()
    i, j = s.find("{"), s.rfind("}")
    if i < 0 or j <= i:
        return None
    try:
        return json.loads(s[i:j + 1])
    except json.JSONDecodeError:
        return None


def post(url, key, payload, timeout=120):
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", "application/json")
    req.add_header("Authorization", "Bearer " + key)
    started = time.time()
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            raw = resp.read().decode("utf-8")
            return resp.status, raw, int((time.time() - started) * 1000)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        return e.code, raw, int((time.time() - started) * 1000)
    except Exception as e:  # noqa: BLE001 - 网络层错误统一回报
        return 0, "%s: %s" % (type(e).__name__, e), int((time.time() - started) * 1000)


def content_of(raw):
    try:
        body = json.loads(raw)
    except json.JSONDecodeError:
        return None, None
    if isinstance(body, dict) and body.get("error"):
        return None, body["error"]
    try:
        msg = body["choices"][0]["message"]["content"]
    except (KeyError, IndexError, TypeError):
        return None, "响应里没有 choices[0].message.content"
    if isinstance(msg, list):  # 带 reasoning 的模型会分段返回
        msg = "".join(p.get("text", "") for p in msg if isinstance(p, dict))
    return msg, None


def find_image(explicit):
    if explicit:
        return explicit if os.path.isfile(explicit) else None
    hits = sorted(glob.glob(os.path.join(UPLOAD_DIR, "**", "*.png"), recursive=True))
    hits = [h for h in hits if "ulcer" in h] or hits
    return hits[0] if hits else None


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", help="用来验证读图链路的图片，默认取上传目录里的演示图")
    ap.add_argument("--timeout", type=int, default=120)
    args = ap.parse_args()

    cfg = load_config()
    vision_base = (cfg.get("vision-base-url") or cfg["base-url"] or "").rstrip("/")
    text_base = (cfg.get("text-base-url") or cfg["base-url"] or "").rstrip("/")
    vision_url = vision_base + "/chat/completions"
    text_url = text_base + "/chat/completions"
    shared_key = os.environ.get("TZ_AI_API_KEY", "").strip()
    vision_key = os.environ.get("TZ_AI_VISION_API_KEY", "").strip() or shared_key
    text_key = os.environ.get("TZ_AI_TEXT_API_KEY", "").strip() or shared_key
    temp = float(cfg["temperature"] or 0.2)

    print("=" * 68)
    print("配置（来自 src/main/resources/application.yml）")
    print("=" * 68)
    for k in KEYS:
        print("  %-13s %s" % (k, cfg[k]))
    for k, jar_v, src_v in cfg.get("_jar_diff", []):
        print("  [!] jar 里是 %s=%s，源码已改成 %s —— 需要重新 package" % (k, jar_v, src_v))
    if not cfg.get("_jar_diff") and cfg.get("_jar_note"):
        print("  [i] 读 jar 里的配置失败（%s），跳过一致性比对" % cfg["_jar_note"])

    if not vision_key and not text_key:
        print("\n[跳过] 未设置大模型 API Key，无法探测。设置后重跑：")
        print('       PowerShell:  $env:TZ_AI_API_KEY = "你的视觉 key"')
        print('       PowerShell:  $env:TZ_AI_TEXT_API_KEY = "你的 DeepSeek key"')
        return 2

    print("\n视觉接口地址：%s" % vision_url)
    print("文本接口地址：%s" % text_url)
    failures = 0

    # ---- 1. 视觉模型 + base64 data URL ----
    print("\n" + "-" * 68)
    print("1/2 视觉模型 %s（图片走 base64 data URL，与后端一致）" % cfg["vision-model"])
    print("-" * 68)
    img = find_image(args.image)
    if not img:
        print("  [跳过] 没找到可用图片，用 --image 指定一张")
    else:
        with open(img, "rb") as f:
            blob = f.read()
        mime = "image/png" if img.lower().endswith(".png") else "image/jpeg"
        data_url = "data:%s;base64,%s" % (mime, base64.b64encode(blob).decode("ascii"))
        print("  图片：%s（%d KB）" % (os.path.basename(img), len(blob) // 1024))

        payload = {
            "model": cfg["vision-model"],
            "temperature": temp,
            "messages": [
                {"role": "system", "content":
                    "你是一名柑橘植保辅助诊断助手。只输出一个 JSON 对象，"
                    "不要输出 JSON 之外的任何文字，不要用 markdown 代码块包裹。\n"
                    "字段：diagnosisName（病虫害或缺素名称）、confidence（0-100 数字）、"
                    "riskLevel（\"1\"/\"2\"/\"3\" 代表低/中/高风险）、basis（判断依据）。\n"
                    "图片看不清或不是植物叶片时，diagnosisName 填 \"无法判断\"，confidence 填 0。"},
                {"role": "user", "content": [
                    {"type": "text", "text":
                        "作物：柑橘\n发生部位：叶片\n严重程度：中度\n"
                        "农技员描述的症状：叶片有黄色晕圈和凸起斑点\n"
                        "请结合随附照片判断，按要求输出 JSON。"},
                    {"type": "image_url", "image_url": {"url": data_url}},
                ]},
            ],
        }
        if not vision_key:
            print("  [跳过] 未设置视觉模型 API Key")
            status, raw, ms = 0, "未设置视觉模型 API Key", 0
        else:
            status, raw, ms = post(vision_url, vision_key, payload, args.timeout)
        if status != 200:
            failures += 1
            print("  [失败] HTTP %s，耗时 %d ms" % (status, ms))
            print("         %s" % raw[:400].replace("\n", " "))
            if "base64" in raw.lower() or "url" in raw.lower():
                print("         提示：该模型可能不收 base64 data URL，换一个支持内联图片的视觉模型。")
        else:
            text, err = content_of(raw)
            if err:
                failures += 1
                print("  [失败] 响应异常：%s" % err)
            else:
                parsed = extract_json(text)
                print("  [通过] HTTP 200，耗时 %d ms，返回 %d 字" % (ms, len(text)))
                if isinstance(parsed, dict) and parsed.get("diagnosisName"):
                    print("         JSON 解析成功：diagnosisName=%s，confidence=%s，riskLevel=%s"
                          % (parsed.get("diagnosisName"), parsed.get("confidence"), parsed.get("riskLevel")))
                    if str(parsed.get("diagnosisName")).strip() in ("无法判断", "不是叶片", "非叶片"):
                        print("         （演示图是程序画的示意图，模型说无法判断属于预期，不代表链路有问题）")
                else:
                    failures += 1
                    print("         [失败] 返回的不是可解析的 JSON，模型没遵守输出约束。原始内容：")
                    print("         %s" % text[:300].replace("\n", " "))

    # ---- 2. 文本模型 ----
    print("\n" + "-" * 68)
    print("2/2 文本模型 %s（问答链路）" % cfg["text-model"])
    print("-" * 68)
    payload = {
        "model": cfg["text-model"],
        "temperature": temp,
        "messages": [
            {"role": "system", "content":
                "你是柑橘植保问答助手。只输出一个 JSON 对象，不要输出 JSON 之外的任何文字，"
                "不要用 markdown 代码块包裹。字段：answer、guidance、basis、disclaimer。\n"
                "禁止编造农药名称与用量：知识库没给的一律写 \"未收录\"。"},
            {"role": "user", "content":
                "【用户提问】柑橘叶片出现黄色晕圈和凸起斑点，是什么问题？\n\n"
                "【知识库参考】\n名称：柑橘溃疡病\n症状：叶片初现针头大小黄绿色小点，"
                "后扩大成近圆形病斑，木栓化隆起，周围有黄色晕圈。\n"
                "用药注意：未收录\n\n请按要求输出 JSON。"},
        ],
    }
    if not text_key:
        print("  [跳过] 未设置文本模型 API Key")
        status, raw, ms = 0, "未设置文本模型 API Key", 0
    else:
        status, raw, ms = post(text_url, text_key, payload, args.timeout)
    if status != 200:
        failures += 1
        print("  [失败] HTTP %s，耗时 %d ms" % (status, ms))
        print("         %s" % raw[:400].replace("\n", " "))
    else:
        text, err = content_of(raw)
        if err:
            failures += 1
            print("  [失败] 响应异常：%s" % err)
        else:
            parsed = extract_json(text)
            print("  [通过] HTTP 200，耗时 %d ms，返回 %d 字" % (ms, len(text)))
            if isinstance(parsed, dict) and parsed.get("answer"):
                print("         JSON 字段齐全：%s" % "、".join(sorted(parsed.keys())))
                print("         answer 开头：%s" % str(parsed.get("answer"))[:60])
            else:
                failures += 1
                print("         [失败] 返回的不是可解析的 JSON。原始内容：")
                print("         %s" % text[:300].replace("\n", " "))

    print("\n" + "=" * 68)
    if failures:
        print("结论：%d 项未通过。上面的原始响应就是服务商的报错，照它改配置。" % failures)
    else:
        print("结论：两条链路都通。环境变量已生效，可以重启后端跑全链路：")
        print("      powershell -NoProfile -File scripts\\start-all.ps1")
        print("      python scripts\\test-api.py      # 第 2 组应显示来源 llm")
    print("=" * 68)
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
