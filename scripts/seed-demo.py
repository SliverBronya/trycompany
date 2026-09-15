#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
田诊助手 演示数据。

做三件事，全部走真实接口，不直接写库：
  1. 生成三张标注清楚是「示意图」的演示图，上传，按图片 MD5 登记为预置样张；
  2. 建一个演示地块，为每张图建一条巡田记录；
  3. 依次调用 诊断 → 防治建议 → 巡田报告，让复查任务由报告环节自动排出。

为什么用脚本而不是纯 SQL：预置样张映射靠图片 MD5 命中，而 MD5 只有把图片真的上传
之后才算得出来。用 SQL 手写哈希，等于把「这张图」和「那条结论」的绑定关系凭空断言一遍，
将来图片换了哈希就对不上了。

另外，演示数据由真实链路跑出来（而不是手写结论塞进数据库），
顺带也就是一次全流程回归：source、degraded、dosageGuardHit 这些字段都是管道真的填的。

用法:
    python scripts/seed-demo.py            # 建演示数据（可重复执行）
    python scripts/seed-demo.py --clean    # 清掉演示数据

只用标准库 + Pillow。Pillow 只用于生成演示图，缺失时会提示并退出。
"""

import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = "http://127.0.0.1:18080"
USERNAME = os.environ.get("TZ_USERNAME", "admin")


def _load_password():
    """取 admin 口令：优先环境变量，其次 scripts/.secrets.local（该文件被 git 忽略）。

    与 test-api.py 用同一套取法。为什么不写默认值：仓库是公开的，
    口令落在代码里会随 fork 与复制一路传下去。
    """
    value = os.environ.get("TZ_PASSWORD")
    if value:
        return value
    secret_file = os.path.join(os.path.dirname(os.path.abspath(__file__)), ".secrets.local")
    if os.path.isfile(secret_file):
        with open(secret_file, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if line.startswith("TZ_PASSWORD="):
                    return line.split("=", 1)[1].strip()
    print("[错误] 没有取到 admin 口令，脚本无法登录。请任选一种方式提供：", file=sys.stderr)
    print("       1) 设置环境变量：TZ_PASSWORD=你的口令", file=sys.stderr)
    print("       2) 在 scripts/.secrets.local 写一行 TZ_PASSWORD=你的口令", file=sys.stderr)
    print("          （照 scripts/.secrets.local.example 复制一份即可）", file=sys.stderr)
    sys.exit(2)


PASSWORD = _load_password()

# 名字对齐 PRD 的演示流程（「七、演示流程」第 2 步：新建地块 柑橘示范园1号地）
# 与开发任务清单第 7.2 条。演示时讲稿里念的名字和看板上出现的名字是同一个，
# 不用现场解释「这两个其实是同一个地块」。
DEMO_PLOT_NAME = "柑橘示范园1号地"
DEMO_TAG = "demo-seed"          # 写进 remark，便于 --clean 精确识别本脚本产生的数据

FONT_CANDIDATES = [
    "C:/Windows/Fonts/msyh.ttc",
    "C:/Windows/Fonts/simhei.ttf",
    "C:/Windows/Fonts/deng.ttf",
]

# 演示用的三条：覆盖「病害 / 虫害 / 营养问题」三类，风险等级各不相同。
#
# 这里的 expect_knowledge_id 是**期望值，不是查找键** —— 真正用的 id 由
# resolve_knowledge_ids() 按病名向知识库查出来。曾经这里写死的 id 是查找键，
# 结果知识库重载后 AUTO_INCREMENT 前移，记录就挂到了不存在的条目上；
# 而建议仍能显示正确病名（SuggestionService 按名称再查一次），所以这个错
# 在界面上完全看不出来。现在改成按名称解析，并且比对不上就大声报警。
DEMO_CASES = [
    {
        "key": "ulcer",
        "disease": "柑橘溃疡病",
        "expect_knowledge_id": 100,
        "risk_level": "2",
        "confidence": 90,
        "basis": "演示示意图：叶片出现近圆形病斑、中央木栓化隆起、周围有黄色晕圈，与知识库溃疡病条目描述一致。",
        "plant_part": "1",
        "severity": "2",
        "symptom": "叶片正反面均有近圆形病斑，中央呈木栓化隆起并开裂，病斑周围有黄色晕圈，叶背病斑隆起更明显。"
                   "近期连续阴雨后转晴，果园通风一般。",
        "color": (198, 143, 62),
    },
    {
        "key": "mite",
        "disease": "柑橘红蜘蛛",
        "expect_knowledge_id": 102,
        "risk_level": "3",
        "confidence": 88,
        "basis": "演示示意图：叶面密布灰白色失绿小点、整体呈灰黄，叶背可见细小虫体与蛛丝，与知识库红蜘蛛条目描述一致。",
        "plant_part": "1",
        "severity": "3",
        "symptom": "叶面密布针尖大小的灰白色失绿斑点，远看整片叶呈灰黄色；翻看叶背可见细小的红色虫体与少量蛛丝。"
                   "近半月高温干旱未降雨。",
        "color": (176, 92, 74),
    },
    {
        "key": "mg",
        "disease": "柑橘缺镁",
        "expect_knowledge_id": 104,
        "risk_level": "1",
        "confidence": 86,
        "basis": "演示示意图：老叶叶脉间呈倒 V 形黄化、叶脉本身仍保持绿色，与知识库缺镁条目描述一致。",
        "plant_part": "1",
        "severity": "1",
        "symptom": "中下部老叶的叶脉间发黄，呈典型的倒 V 形黄化，叶脉及附近组织仍为绿色；"
                   "新叶基本正常。果园为砂质土，往年未补过镁肥。",
        "color": (150, 158, 88),
    },
]


# ---------------------------------------------------------------- HTTP

_token = None


def call(method, path, body=None, params=None):
    url = BASE + path
    if params:
        from urllib.parse import urlencode
        url += "?" + urlencode(params)

    data = None
    headers = {"Content-Type": "application/json;charset=utf-8"}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    if _token and not path.startswith("/login"):
        headers["Authorization"] = "Bearer " + _token

    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            payload = resp.read().decode("utf-8")
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8")
    try:
        return json.loads(payload)
    except json.JSONDecodeError:
        return {"code": -1, "msg": payload[:400]}


def upload(file_path):
    """按 multipart/form-data 上传一个文件，返回接口给的访问地址。"""
    boundary = "----tzdemo" + uuid.uuid4().hex
    filename = os.path.basename(file_path)
    with open(file_path, "rb") as f:
        content = f.read()

    parts = []
    parts.append(("--%s\r\n" % boundary).encode())
    parts.append(('Content-Disposition: form-data; name="file"; filename="%s"\r\n' % filename).encode())
    parts.append(b"Content-Type: image/png\r\n\r\n")
    parts.append(content)
    parts.append(("\r\n--%s--\r\n" % boundary).encode())
    payload = b"".join(parts)

    req = urllib.request.Request(BASE + "/common/upload", data=payload, method="POST")
    req.add_header("Content-Type", "multipart/form-data; boundary=" + boundary)
    if _token:
        req.add_header("Authorization", "Bearer " + _token)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return {"code": e.code, "msg": e.read().decode("utf-8")[:400]}


# ---------------------------------------------------------------- 演示图

def make_image(path, case):
    """
    画一张演示用的示意图。

    图里直接写明「示意图/非真实照片」：预置样张的作用是让演示稳定复现，
    不是冒充一张真实病叶照片。标注留在图上，截图流出去也不会被误读。
    """
    from PIL import Image, ImageDraw, ImageFont

    font_path = next((p for p in FONT_CANDIDATES if os.path.exists(p)), None)
    if not font_path:
        raise SystemExit("找不到中文字体，无法在演示图上写标注；请检查 C:/Windows/Fonts。")

    title_font = ImageFont.truetype(font_path, 30)
    body_font = ImageFont.truetype(font_path, 20)
    tag_font = ImageFont.truetype(font_path, 22)

    w, h = 720, 540
    img = Image.new("RGB", (w, h), (244, 246, 241))
    d = ImageDraw.Draw(img)

    # 叶片轮廓
    leaf = [(110, 400), (200, 190), (360, 110), (540, 150), (610, 330), (430, 460), (230, 455)]
    d.polygon(leaf, fill=(126, 168, 96), outline=(74, 110, 56))
    d.line([(140, 395), (585, 175)], fill=(74, 110, 56), width=5)

    # 病征示意斑块
    d.ellipse([250, 230, 330, 300], fill=case["color"], outline=(90, 60, 40))
    d.ellipse([380, 250, 450, 315], fill=case["color"], outline=(90, 60, 40))
    d.ellipse([315, 330, 380, 390], fill=case["color"], outline=(90, 60, 40))

    # 顶部标签
    d.rectangle([0, 0, w, 62], fill=(38, 52, 74))
    d.text((20, 15), "示意图 · 非真实病叶照片 ｜ %s" % case["disease"], font=title_font, fill=(255, 255, 255))

    # 底部说明
    d.rectangle([0, h - 96, w, h], fill=(255, 255, 255))
    d.text((20, h - 88), "用途：演示预置样张保底路径（同一张图每次得到同一结论）", font=body_font, fill=(60, 60, 60))
    d.text((20, h - 60), "正式使用时请替换为贵方实拍照片，并在植保知识库中补齐资料来源。", font=body_font, fill=(140, 60, 60))
    d.text((20, h - 32), "田诊助手 · 演示数据", font=tag_font, fill=(120, 130, 145))

    img.save(path, "PNG")
    return path


# ---------------------------------------------------------------- 流程

def login():
    global _token
    body = call("POST", "/login", {"username": USERNAME, "password": PASSWORD})
    _token = body.get("token")
    if not _token:
        raise SystemExit("登录失败：%s" % body)
    print("已登录 %s" % USERNAME)


def resolve_knowledge_ids():
    """按病名查出知识库真实 id，写回每个 case。与期望值不符时明确报警。

    不写死 id 的原因见 DEMO_CASES 上方注释：写死的 id 会随知识库重载漂移，
    而且漂移后界面上看不出异常（建议按名称解析，仍显示正确病名）。
    """
    body = call("GET", "/tz/knowledge/list", params={"pageSize": 100})
    id_by_name = {}
    for row in (body.get("rows") or []):
        id_by_name[row.get("diseaseName")] = row.get("knowledgeId")

    for case in DEMO_CASES:
        real = id_by_name.get(case["disease"])
        if real is None:
            raise SystemExit(
                "知识库里没有「%s」这条：先导入 sql/tz_knowledge_seed.sql" % case["disease"])
        expect = case.get("expect_knowledge_id")
        if expect is not None and expect != real:
            print("  ⚠️ %s 的知识库 id 与预期不符：预期 %s，实际 %s（已按实际值继续）"
                  % (case["disease"], expect, real))
        case["knowledge_id"] = real
        print("  知识库条目：%s -> id=%s" % (case["disease"], real))


def find_plot():
    body = call("GET", "/tz/plot/list", params={"plotName": DEMO_PLOT_NAME})
    rows = body.get("rows") or []
    return rows[0]["plotId"] if rows else None


def ensure_plot():
    plot_id = find_plot()
    if plot_id:
        print("演示地块已存在：id=%d" % plot_id)
        return plot_id
    body = call("POST", "/tz/plot", {
        "plotName": DEMO_PLOT_NAME, "cropType": "柑橘", "area": 68.5,
        "location": "演示用 · 请替换为真实地块信息", "ownerName": "示范园",
        "plantYear": 2019, "status": "0", "remark": DEMO_TAG,
    })
    if body.get("code") != 200:
        raise SystemExit("建演示地块失败：%s" % body)
    plot_id = find_plot()
    print("已建演示地块：id=%s" % plot_id)
    return plot_id


def ensure_preset(image_path, url, case):
    """按图片真实 MD5 登记预置样张；已登记过就直接复用。

    这里同时登记 symptom_text（照片上看得见的症状）。它是「照片自动生成描述」
    在未配 API Key 时的兜底来源：演示现场只要上传这三张样图，描述框就会被自动填好，
    不依赖大模型。文案与下面建巡田记录时用的 case["symptom"] 是同一份，
    免得脚本新建的和库里已有的变成两份不一样的话。
    """
    with open(image_path, "rb") as f:
        md5 = hashlib.md5(f.read()).hexdigest()

    body = call("GET", "/tz/preset/list", params={"imageHash": md5})
    if (body.get("rows") or []):
        print("  预置样张已登记：%s -> %s" % (md5[:12] + "…", case["disease"]))
        return md5

    body = call("POST", "/tz/preset", {
        "imageHash": md5,
        "imageName": os.path.basename(image_path),
        "imageUrl": url,
        "cropType": "柑橘",
        "diagnosisName": case["disease"],
        "symptomText": case["symptom"],
        "confidence": case["confidence"],
        "riskLevel": case["risk_level"],
        "diagnosisBasis": case["basis"],
        "knowledgeId": case["knowledge_id"],
        "status": "0",
        "remark": DEMO_TAG + "：演示示意图，结论来自已登记的标准答案，不经过大模型",
    })
    if body.get("code") != 200:
        raise SystemExit("登记预置样张失败：%s" % body)
    print("  已登记预置样张：%s -> %s" % (md5[:12] + "…", case["disease"]))
    return md5


def find_record(plot_id, case):
    body = call("GET", "/tz/record/list", params={"plotId": plot_id})
    for row in (body.get("rows") or []):
        if str(row.get("symptomText") or "").startswith(case["symptom"][:18]):
            return row["recordId"]
    return None


def run_case(plot_id, tmpdir, case):
    print("\n=== %s ===" % case["disease"])
    image_path = os.path.join(tmpdir, "demo-%s.png" % case["key"])
    make_image(image_path, case)

    body = upload(image_path)
    if body.get("code") != 200 or not body.get("url"):
        raise SystemExit("上传演示图失败：%s" % body)
    url = body["url"]
    print("  已上传：%s" % url)

    ensure_preset(image_path, url, case)

    record_id = find_record(plot_id, case)
    if record_id:
        print("  巡田记录已存在：id=%d" % record_id)
    else:
        body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": case["plant_part"], "severity": case["severity"],
            "symptomText": case["symptom"], "imageUrl": url, "status": "0",
            "remark": DEMO_TAG,
        })
        if body.get("code") != 200:
            raise SystemExit("建巡田记录失败：%s" % body)
        record_id = (body.get("data") or {}).get("recordId")
        print("  已建巡田记录：id=%s" % record_id)

    # 诊断：走真实接口。图片已登记，因此应当命中预置路径
    body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": record_id, "useLlm": "true"})
    if body.get("code") != 200:
        raise SystemExit("诊断失败：%s" % body)
    d = body.get("data") or {}
    print("  诊断：%s（来源 %s，置信度 %s，风险 %s）"
          % (d.get("diagnosisName"), d.get("source"), d.get("confidence"), d.get("riskLevel")))
    if d.get("source") != "preset":
        print("  !! 预期命中预置样张，实际来源是 %s —— 请检查上传的图片与登记的 MD5 是否一致"
              % d.get("source"))

    body = call("POST", "/tz/suggestion/generate/%d" % record_id, params={"useLlm": "true"})
    s = body.get("data") or {}
    print("  防治建议：来源 %s%s" % (s.get("source"),
          "（剂量校验拦截了 %d 处用量）" % len(s.get("rejectedDosages") or []) if s.get("dosageGuardHit") else ""))

    body = call("POST", "/tz/report/generate/%d" % record_id, params={"useLlm": "true"})
    r = body.get("data") or {}
    print("  巡田报告：%s 复查任务；截止 %s"
          % ("已自动排" if r.get("followUpTaskCreated") else "复用已有", r.get("followUpDueDate")))
    return {"record_id": record_id, "task_id": r.get("followUpTaskId")}


def clean():
    print("清理演示数据…")
    body = call("GET", "/tz/preset/list", params={"pageSize": 200})
    for row in (body.get("rows") or []):
        if DEMO_TAG in str(row.get("remark") or ""):
            call("DELETE", "/tz/preset/%d" % row["presetId"])
            print("  删除预置样张 %s" % row.get("imageName"))

    plot_id = find_plot()
    if plot_id:
        body = call("GET", "/tz/record/list", params={"plotId": plot_id, "pageSize": 200})
        for row in (body.get("rows") or []):
            call("DELETE", "/tz/record/%d" % row["recordId"])
        print("  删除演示记录 %d 条" % len(body.get("rows") or []))
        body = call("DELETE", "/tz/plot/%d" % plot_id)
        print("  删除演示地块：%s" % ("成功" if body.get("code") == 200 else body))
    print("完成。（上传的演示图文件仍留在上传目录，不影响使用）")


def main():
    if "--clean" in sys.argv:
        login()
        clean()
        return 0

    try:
        import PIL  # noqa: F401
    except ImportError:
        raise SystemExit("需要 Pillow 来生成演示图：pip install pillow")

    import tempfile

    login()
    resolve_knowledge_ids()
    plot_id = ensure_plot()

    results = []
    with tempfile.TemporaryDirectory(prefix="tz-demo-") as tmpdir:
        for case in DEMO_CASES:
            results.append(run_case(plot_id, tmpdir, case))

    print("\n" + "=" * 58)
    print("演示数据就绪，共 %d 条巡田记录，地块：%s" % (len(results), DEMO_PLOT_NAME))
    print("提示：演示图是标注过的「示意图」，正式使用前请替换为实拍照片。")
    print("      知识库 15 条已带真实出处与用量，可按需增补本地病虫情报。")
    print("=" * 58)
    return 0


if __name__ == "__main__":
    sys.exit(main())
