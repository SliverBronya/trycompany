#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""
田诊助手 后端接口端到端自测

用法:
    python scripts/test-api.py                # 全量
    python scripts/test-api.py plot           # 只跑名字里含 plot 的用例

设计意图：每改一轮代码就跑一次，不用手点页面。任一断言失败会打印实际响应体，
退出码非 0，方便接进脚本。

只用标准库，不依赖 requests。
"""

import json
import os
import re
import sys
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:18080"
USERNAME = os.environ.get("TZ_USERNAME", "admin")


def _load_password():
    """取 admin 口令：优先环境变量，其次 scripts/.secrets.local（该文件被 git 忽略）。

    为什么不把口令写成默认值：仓库是公开的。口令一旦落在代码里，
    就会随着 fork 与复制一路传下去，而它对应的还是一个能打开的演示站。
    真实口令放在 .secrets.local 里，本机照常能用，仓库里只有模板。
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


# 口令在挂公网之前改过一次（原来是 admin123，见 docs\操作手册.md）。
PASSWORD = _load_password()

# 自测记录带的图片地址。
#
# 诊断、闭环这几条用例验的是编排与降级逻辑，本身不依赖照片内容；但
# 「新建巡田必须带照片」是一条硬校验，所以每条自测记录都得带上。
# 指向一个并不存在的文件是有意的：ImageHash 取不到文件返回 null，
# 于是跳过预置匹配 —— 这恰好让「无 hash → 知识库降级」这条路稳定可复现。
SELFTEST_IMAGE_URL = "/profile/upload/self-test.jpg"

# ---------------------------------------------------------------- HTTP 封装

_token = None


def call(method, path, body=None, params=None, raw=False):
    """发一个请求，返回 (http_status, 解析后的 body)。"""
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
        with urllib.request.urlopen(req, timeout=60) as resp:
            payload = resp.read().decode("utf-8")
            status = resp.status
    except urllib.error.HTTPError as e:
        payload = e.read().decode("utf-8")
        status = e.code

    if raw:
        return status, payload
    try:
        return status, json.loads(payload)
    except json.JSONDecodeError:
        return status, payload


# ---------------------------------------------------------------- 断言框架

_results = []
_created = {}   # 用例之间传递的 ID


def check(name, condition, detail=""):
    _results.append((name, bool(condition), detail))
    mark = "PASS" if condition else "FAIL"
    print("  [%s] %s%s" % (mark, name, ("  -> " + str(detail)) if (detail and not condition) else ""))
    return bool(condition)


def section(title):
    print("\n" + "=" * 62)
    print(title)
    print("=" * 62)


# ---------------------------------------------------------------- 用例

def case_login():
    section("1. 登录 / 鉴权")
    global _token
    status, body = call("POST", "/login", {"username": USERNAME, "password": PASSWORD})
    ok = check("登录返回 200", status == 200, body)
    if not ok:
        return False
    _token = body.get("token")
    check("拿到 JWT token", bool(_token), body)
    return bool(_token)


def case_plot():
    section("2. 地块 CRUD")
    stamp = str(int(time.time()))[-6:]
    name = "自测地块-" + stamp

    _, body = call("POST", "/tz/plot", {
        "plotName": name, "cropType": "柑橘", "area": 12.5,
        "location": "自测果园 A 区", "ownerName": "自测员", "plantYear": 2019, "status": "0",
    })
    ok = check("新增地块", body.get("code") == 200, body)
    if not ok:
        return False

    _, body = call("GET", "/tz/plot/list", params={"plotName": name})
    rows = body.get("rows", [])
    check("列表能查到刚建的地块", len(rows) == 1, body)
    if not rows:
        return False
    plot_id = rows[0]["plotId"]
    _created["plotId"] = plot_id
    check("列表带出地块名称", rows[0].get("plotName") == name, rows[0])

    _, body = call("POST", "/tz/plot", {"plotName": name, "cropType": "柑橘"})
    check("重名地块被拒绝", body.get("code") == 500 and "已存在" in str(body.get("msg")), body)

    _, body = call("POST", "/tz/plot", {"cropType": "柑橘"})
    check("缺地块名称被校验拦截", body.get("code") == 500, body)

    _, body = call("GET", "/tz/plot/%d" % plot_id)
    check("按 ID 查详情", body.get("code") == 200 and body["data"]["plotId"] == plot_id, body)

    _, body = call("PUT", "/tz/plot", {"plotId": plot_id, "plotName": name, "area": 20, "status": "0"})
    check("修改地块", body.get("code") == 200, body)

    _, body = call("GET", "/tz/plot/optionselect")
    check("下拉选项接口可用", body.get("code") == 200 and isinstance(body.get("data"), list), body)
    return True


def case_record():
    section("3. 巡田记录 CRUD")
    plot_id = _created.get("plotId")
    if not plot_id:
        check("依赖地块用例", False, "地块未创建成功，跳过")
        return False

    _, body = call("POST", "/tz/record", {
        "plotId": plot_id, "plantPart": "1",
        "symptomText": "自测：叶片出现近圆形褪绿斑，边缘褐色",
        "severity": "2", "imageUrl": SELFTEST_IMAGE_URL,
    })
    ok = check("新增巡田记录", body.get("code") == 200, body)
    if not ok:
        return False
    record_id = body.get("data", {}).get("recordId")
    check("回填 recordId（供后续诊断调用）", bool(record_id), body)
    _created["recordId"] = record_id

    _, body = call("GET", "/tz/record/list", params={"plotId": plot_id})
    check("按地块过滤记录", body.get("total", 0) >= 1, body)
    if body.get("rows"):
        row = body["rows"][0]
        check("列表联表带出地块名称", row.get("plotName") is not None, row)
        check("列表联表带出作物类型", "cropType" in row, row)

    _, body = call("GET", "/tz/record/%d" % record_id)
    check("按 ID 查记录详情", body.get("code") == 200 and body["data"]["recordId"] == record_id, body)

    _, body = call("GET", "/tz/record/list", params={"riskLevel": "3", "plotId": plot_id})
    check("按风险等级过滤不报错", body.get("code") == 200, body)

    _, body = call("GET", "/tz/record/list", params={"params[beginTime]": "2020-01-01", "params[endTime]": "2099-01-01"})
    check("时间区间过滤不报错", body.get("code") == 200, body)
    return True


def case_followup():
    section("4. 复查任务")
    record_id = _created.get("recordId")
    plot_id = _created.get("plotId")
    if not record_id:
        check("依赖巡田记录用例", False, "记录未创建成功，跳过")
        return False

    due = time.strftime("%Y-%m-%d", time.localtime(time.time() + 7 * 86400))
    _, body = call("POST", "/tz/followup", {
        "recordId": record_id, "plotId": plot_id,
        "taskTitle": "自测复查任务", "dueDate": due, "status": "0",
    })
    ok = check("新增复查任务", body.get("code") == 200, body)
    if not ok:
        return False

    _, body = call("GET", "/tz/followup/list", params={"plotId": plot_id})
    rows = body.get("rows", [])
    check("列表能查到复查任务", len(rows) >= 1, body)
    if not rows:
        return False
    task_id = rows[0]["taskId"]
    check("列表联表带出诊断结果列", "diagnosisName" in rows[0], rows[0])

    _, body = call("PUT", "/tz/followup/status", {"taskId": task_id, "status": "1", "note": "自测完成"})
    check("标记复查完成", body.get("code") == 200, body)

    _, body = call("GET", "/tz/followup/%d" % task_id)
    check("完成后写入 finishTime", body.get("code") == 200 and body["data"].get("finishTime"), body)

    _, body = call("GET", "/tz/followup/list", params={"status": "0", "plotId": plot_id})
    check("按状态过滤不报错", body.get("code") == 200, body)
    return True


def case_knowledge():
    section("5. 植保知识库")
    _, body = call("GET", "/tz/knowledge/list")
    check("知识库列表可读", body.get("code") == 200 and body.get("total", 0) >= 0, body)
    rows = body.get("rows", [])
    if rows:
        first = rows[0]
        check("知识库条目带来源标注", bool(first.get("source")), first)
        _, body = call("GET", "/tz/knowledge/%d" % first["knowledgeId"])
        check("按 ID 查条目详情", body.get("code") == 200, body)
    else:
        print("  [SKIP] 知识库暂无数据（种子数据导入后此段会实际校验）")
    return True


def case_dashboard():
    section("6. 首页看板")
    _, body = call("GET", "/tz/dashboard/stats")
    check("stats 返回 200", body.get("code") == 200, body)
    data = body.get("data") or {}
    for key in ("totalRecords", "plotCount", "pendingTaskCount"):
        check("stats 含 %s" % key, key in data, data)

    _, body = call("GET", "/tz/dashboard/charts")
    check("charts 返回 200", body.get("code") == 200, body)
    data = body.get("data") or {}
    for key in ("diagnosisDistribution", "riskDistribution", "sourceDistribution", "scoutTrend"):
        check("charts 含 %s" % key, key in data, data)
        check("%s 是数组" % key, isinstance(data.get(key), list), data.get(key))
    return True


def case_diagnosis():
    section("7. AI 诊断链路（预置 → 大模型 → 关键词降级）")
    stamp = str(int(time.time()))[-6:]

    # --- 7.1 当前 AI 配置状态 ---
    _, body = call("GET", "/tz/diagnosis/config")
    check("config 可读", body.get("code") == 200, body)
    cfg = body.get("data") or {}
    llm_ready = cfg.get("llmAvailable")
    print("       当前模式：%s" % cfg.get("modeText"))
    check("config 标明是否接入大模型", "llmAvailable" in cfg, cfg)
    check("config 带免责声明", bool(cfg.get("disclaimer")), cfg)

    # --- 7.2 准备地块 ---
    _, body = call("POST", "/tz/plot", {"plotName": "诊断自测-" + stamp, "cropType": "柑橘", "status": "0"})
    _, body = call("GET", "/tz/plot/list", params={"plotName": "诊断自测-" + stamp})
    rows = body.get("rows", [])
    if not check("准备诊断用地块", len(rows) == 1, body):
        return False
    plot_id = rows[0]["plotId"]
    created_records = []

    try:
        # --- 7.3 预置映射路径：登记一个样张，同 hash 必得同结论 ---
        fake_hash = "selftest" + stamp + "0" * 48
        fake_hash = fake_hash[:64].ljust(64, "0")
        _, body = call("POST", "/tz/preset", {
            "imageHash": fake_hash, "imageName": "自测样张",
            "diagnosisName": "柑橘溃疡病", "confidence": 92.5, "riskLevel": "2",
            "diagnosisBasis": "自测用预置依据", "status": "0",
        })
        preset_ok = check("登记预置样张映射", body.get("code") == 200, body)
        preset_id = (body.get("data") or {}).get("presetId") if preset_ok else None

        if preset_ok:
            _, body = call("POST", "/tz/record", {
                "plotId": plot_id, "plantPart": "1", "severity": "2",
                "symptomText": "自测：叶片有黄色晕圈病斑", "imageHash": fake_hash,
                "imageUrl": SELFTEST_IMAGE_URL,
            })
            rid = body["data"]["recordId"]
            created_records.append(rid)

            _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid, "useLlm": "true"})
            check("诊断接口返回成功", body.get("code") == 200, body)
            d = body.get("data") or {}
            check("预置路径 source=preset", d.get("source") == "preset", d)
            check("预置路径结论正确", d.get("diagnosisName") == "柑橘溃疡病", d)
            check("预置路径置信度透传", float(d.get("confidence") or 0) == 92.5, d)
            check("预置路径未标记降级", d.get("degraded") is False, d)
            check("预置路径带来源说明", bool(d.get("sourceText")), d)
            check("预置结论已回写记录", _record_diagnosis(rid) == "柑橘溃疡病", None)

            # 同一条记录再诊断一次，结论必须完全一致（预置保底的意义所在）
            _, body2 = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid, "useLlm": "true"})
            d2 = body2.get("data") or {}
            check("重复诊断结论稳定", d2.get("diagnosisName") == d.get("diagnosisName")
                  and d2.get("confidence") == d.get("confidence"), (d, d2))

        # --- 7.4 无 hash 时：知识库关键词降级（未配 key 的必然路径） ---
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1", "severity": "2",
            "symptomText": "叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈，叶背隆起明显",
            "imageUrl": SELFTEST_IMAGE_URL,
        })
        rid_kb = body["data"]["recordId"]
        created_records.append(rid_kb)

        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid_kb, "useLlm": "true"})
        check("诊断接口返回成功（降级路径）", body.get("code") == 200, body)
        d = body.get("data") or {}
        expected = "llm" if llm_ready else "fallback"
        check("按是否配 key 落到预期来源（%s）" % expected, d.get("source") == expected, d)
        check("降级路径给出可解释结论", bool(d.get("diagnosisName")), d)
        check("降级路径带降级原因", (not d.get("degraded")) or bool(d.get("degradeReason")), d)
        if d.get("diagnosisName"):
            check("结论绑定到知识库条目（建议可溯）", d.get("knowledgeId") is not None, d)

        # --- 7.5 强行关闭大模型，验证降级开关生效 ---
        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid_kb, "useLlm": "false"})
        d = body.get("data") or {}
        check("useLlm=false 时一定不走大模型", d.get("source") == "fallback", d)

        # --- 7.6 完全无线索时应如实报错，而不是编一个结论 ---
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1",
            "symptomText": "zzz 无关内容 qqq",
            "imageUrl": SELFTEST_IMAGE_URL,
        })
        rid_none = body["data"]["recordId"]
        created_records.append(rid_none)
        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid_none, "useLlm": "true"})
        check("无线索时明确报错而非编造", body.get("code") == 500, body)
        check("报错信息说明原因", "无法给出可靠结论" in str(body.get("msg")), body)

        # --- 7.7 只读查询已存结论 ---
        if created_records:
            _, body = call("GET", "/tz/diagnosis/result/%d" % created_records[0])
            check("result 接口读已存结论", body.get("code") == 200
                  and (body.get("data") or {}).get("diagnosisName") == "柑橘溃疡病", body)

        # --- 7.8 不存在的记录 ---
        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": 99999999})
        check("诊断不存在的记录被拒", body.get("code") == 500, body)

    finally:
        for rid in created_records:
            call("DELETE", "/tz/record/%d" % rid)
        if preset_id:
            call("DELETE", "/tz/preset/%d" % preset_id)
        call("DELETE", "/tz/plot/%d" % plot_id)
    return True


def _record_diagnosis(record_id):
    _, body = call("GET", "/tz/record/%d" % record_id)
    return (body.get("data") or {}).get("diagnosisName")


def case_describe():
    """照片必填 + 症状描述自动生成。

    这一节守的是两条产品规则，二者是一起改的，断言也放在一起：
      - 照片是硬性必填（诊断以图像为主要依据，预置匹配更是只看图片哈希）；
      - 症状描述不再必填，改由系统看照片先写一版，用户可以改、也可以整个不写。
    规则一起改是因为它们互为前提：只把描述改成可选、照片仍可空，
    就等于允许一条既没图也没描述、根本没法诊断的记录进库。
    """
    section("7.9 照片必填与症状描述自动生成")
    stamp = str(int(time.time()))[-6:]

    _, body = call("POST", "/tz/plot", {"plotName": "描述自测-" + stamp, "cropType": "柑橘", "status": "0"})
    _, body = call("GET", "/tz/plot/list", params={"plotName": "描述自测-" + stamp})
    rows = body.get("rows", [])
    if not check("准备描述自测地块", len(rows) == 1, body):
        return False
    plot_id = rows[0]["plotId"]
    created_records = []

    try:
        # --- 7.9.1 不带照片应当被拒 ---
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1", "severity": "2",
            "symptomText": "自测：只有文字没有照片",
        })
        check("缺照片时拒绝新建", body.get("code") == 500, body)
        check("拒绝原因说清要先传照片", "上传现场照片" in str(body.get("msg")), body)

        # --- 7.9.2 带照片但不填描述应当通过（描述已改为可选） ---
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1", "severity": "2",
            "imageUrl": SELFTEST_IMAGE_URL,
        })
        ok = check("不填症状描述也能新建", body.get("code") == 200, body)
        if ok:
            rid = (body.get("data") or {}).get("recordId")
            created_records.append(rid)
            check("新建后回填 recordId", bool(rid), body)

        # --- 7.9.3 识别接口的参数校验 ---
        _, body = call("POST", "/tz/record/describe-image", params={"plantPart": "1"})
        check("缺图片地址时识别接口不返回成功", body.get("code") != 200, body)

        # --- 7.9.4 指向取不到的图片：必须如实说没生成，而不是回一段空描述冒充成功 ---
        _, body = call("POST", "/tz/record/describe-image",
                       params={"imageUrl": "/profile/upload/tz-no-such-file.png", "plantPart": "1"})
        check("识别接口返回成功响应", body.get("code") == 200, body)
        d = body.get("data") or {}
        check("取不到图片时 success=false（不编描述）", d.get("success") is False, d)
        check("未生成描述时 description 为空", not d.get("description"), d)
        check("并且说明了没能生成的原因", bool(d.get("degradeReason")), d)
        check("响应带回图片地址，供前端丢弃过期结果", d.get("imageUrl") == "/profile/upload/tz-no-such-file.png", d)

        # --- 7.9.5 登记了描述的预置样张：识别应直接采用它（离线也能填出描述） ---
        # 依赖 seed-demo.py 播种的演示样张。没有就明说跳过，不假装验过：
        # 这条路径是「不配 API Key 也能自动填描述」的落点，值得每次回归都看一眼。
        _, body = call("GET", "/tz/preset/list", params={"pageNum": 1, "pageSize": 20})
        preset_row = next((r for r in (body.get("rows") or []) if r.get("symptomText")), None)
        if preset_row:
            _, body = call("POST", "/tz/record/describe-image",
                           params={"imageUrl": preset_row["imageUrl"], "plantPart": "1"})
            d = body.get("data") or {}
            check("预置样张直接给出登记的描述", d.get("source") == "preset", d)
            check("描述与登记内容一致", d.get("description") == preset_row["symptomText"], d)
            check("来源说明写明来自预置样张", "预置样张" in str(d.get("sourceText")), d)
        else:
            print("      [SKIP] 库里没有登记症状描述的预置样张，"
                  "该路径未验证（先跑 python scripts\\seed-demo.py）")
    finally:
        for rid in created_records:
            call("DELETE", "/tz/record/%d" % rid)
        call("DELETE", "/tz/plot/%d" % plot_id)
    return True


def case_closure():
    section("8. 闭环：防治建议 → 巡田报告 → 复查任务")
    stamp = str(int(time.time()))[-6:]

    _, body = call("POST", "/tz/plot", {"plotName": "闭环自测-" + stamp, "cropType": "柑橘", "status": "0"})
    _, body = call("GET", "/tz/plot/list", params={"plotName": "闭环自测-" + stamp})
    rows = body.get("rows", [])
    if not check("准备闭环自测地块", len(rows) == 1, body):
        return False
    plot_id = rows[0]["plotId"]
    records = []

    try:
        # 统一用 useLlm=false 跑：闭环逻辑不该依赖是否配了 key，这样断言才稳定
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1", "severity": "2",
            "symptomText": "叶片出现近圆形病斑，中央木栓化隆起，周围有黄色晕圈，叶背隆起明显",
            "imageUrl": SELFTEST_IMAGE_URL,
        })
        rid = body["data"]["recordId"]
        records.append(rid)

        # --- 8.1 未诊断就生成建议应被拦下 ---
        _, body = call("POST", "/tz/suggestion/generate/%d" % rid, params={"useLlm": "false"})
        check("未诊断时拒绝生成建议", body.get("code") == 500, body)
        check("拒绝原因说清要先诊断", "尚未完成诊断" in str(body.get("msg")), body)

        # --- 8.2 先诊断，再生成建议 ---
        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid, "useLlm": "false"})
        diagnosed = check("诊断成功（保底路径）", body.get("code") == 200, body)
        if not diagnosed:
            return False

        _, body = call("POST", "/tz/suggestion/generate/%d" % rid, params={"useLlm": "false"})
        check("建议接口返回成功", body.get("code") == 200, body)
        s = body.get("data") or {}
        check("未用大模型时来源标注为 kb", s.get("source") == "kb", s)
        check("kb 路径标记为降级且说明原因", s.get("degraded") is True and bool(s.get("degradeReason")), s)
        check("建议正文非空", bool(s.get("suggestion")), s)
        check("建议带免责声明", "免责声明" in str(s.get("suggestion")), s)
        check("建议带知识库出处（可溯）", bool(s.get("knowledgeSource")), s)
        check("剂量校验结果有明确取值", isinstance(s.get("dosageGuardHit"), bool), s)

        # --- 8.3 建议已回写记录 ---
        _, body = call("GET", "/tz/record/%d" % rid)
        rec = body.get("data") or {}
        check("建议已回写巡田记录", bool(rec.get("suggestion")), rec)
        check("建议来源已回写", rec.get("suggestionSource") == "kb", rec)
        check("剂量拦截标记已回写", rec.get("dosageGuardHit") in ("0", "1"), rec)

        # --- 8.4 只读查询与生成结果一致 ---
        _, body = call("GET", "/tz/suggestion/result/%d" % rid)
        check("建议只读查询一致", (body.get("data") or {}).get("suggestion") == s.get("suggestion"), body)

        # --- 8.5 生成报告（应同时自动排复查任务） ---
        _, body = call("POST", "/tz/report/generate/%d" % rid, params={"useLlm": "false"})
        check("报告接口返回成功", body.get("code") == 200, body)
        rep = body.get("data") or {}
        check("报告正文非空", bool(rep.get("reportText")), rep)
        check("报告复用了已生成的建议", rep.get("suggestionReused") is True, rep)
        check("报告自动创建了复查任务", rep.get("followUpTaskCreated") is True, rep)
        task_id = rep.get("followUpTaskId")
        check("复查任务ID非空", task_id is not None, rep)
        # 日期字段要按项目的 @JsonFormat 约定出，不能漏成 ISO 带时区的串
        check("复查任务带截止日期", bool(rep.get("followUpDueDate")), rep)
        check("截止日期格式为 yyyy-MM-dd",
              bool(re.match(r"^\d{4}-\d{2}-\d{2}$", str(rep.get("followUpDueDate")))),
              rep.get("followUpDueDate"))
        check("报告生成时间格式为 yyyy-MM-dd HH:mm:ss",
              bool(re.match(r"^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$", str(rep.get("reportTime")))),
              rep.get("reportTime"))
        check("报告正文含复查事项", bool(rep.get("followUpTaskTitle"))
              and rep.get("followUpTaskTitle") in str(rep.get("reportText")), rep)
        check("报告带免责声明", "免责声明" in str(rep.get("reportText")), None)

        # --- 8.6 重复生成报告不该重复排任务 ---
        _, body = call("POST", "/tz/report/generate/%d" % rid, params={"useLlm": "false"})
        rep2 = body.get("data") or {}
        check("重复生成报告不重复排任务", rep2.get("followUpTaskCreated") is False, rep2)
        check("重复生成复用同一个任务", rep2.get("followUpTaskId") == task_id, (rep2, task_id))

        _, body = call("GET", "/tz/followup/list", params={"recordId": rid})
        tasks = body.get("rows", [])
        check("该记录下有且仅有 1 条复查任务", len(tasks) == 1, body)
        check("复查任务初始为待复查", tasks and tasks[0].get("status") == "0", tasks)
        check("复查任务标题带地块名", tasks and "闭环自测-" + stamp in tasks[0].get("taskTitle", ""), tasks)

        # --- 8.7 完成复查应把巡田记录推进到「已复查」 ---
        _, body = call("PUT", "/tz/followup/status", {"taskId": task_id, "status": "1", "note": "自测完成"})
        check("标记复查完成", body.get("code") == 200, body)

        _, body = call("GET", "/tz/record/%d" % rid)
        check("巡田记录状态推进到已复查(4)", (body.get("data") or {}).get("status") == "4", body)

        # --- 8.8 复查完毕后重新生成报告，应另排一次（病情反复是常事） ---
        _, body = call("POST", "/tz/report/generate/%d" % rid, params={"useLlm": "false"})
        rep3 = body.get("data") or {}
        check("复查完结后重新生成会另排任务", rep3.get("followUpTaskCreated") is True, rep3)

        # 状态已到 4，不能被打回「已生成报告(2)」
        _, body = call("GET", "/tz/record/%d" % rid)
        check("重生成报告不回退状态", (body.get("data") or {}).get("status") == "4", body)

        # --- 8.9 报告只读查询 ---
        _, body = call("GET", "/tz/report/result/%d" % rid)
        check("报告只读查询读到已存正文", (body.get("data") or {}).get("reportText") == rep.get("reportText"), body)

        # --- 8.10 未诊断记录不生成报告 ---
        _, body = call("POST", "/tz/record", {"plotId": plot_id, "plantPart": "1",
                                              "symptomText": "自测未诊断",
                                              "imageUrl": SELFTEST_IMAGE_URL})
        rid_blank = body["data"]["recordId"]
        records.append(rid_blank)
        _, body = call("POST", "/tz/report/generate/%d" % rid_blank, params={"useLlm": "false"})
        check("未诊断时拒绝生成报告", body.get("code") == 500, body)

        # --- 8.11 报告正文必须转义不可信输入（前端 v-html 渲染） ---
        _, body = call("POST", "/tz/record", {
            "plotId": plot_id, "plantPart": "1", "severity": "1",
            "symptomText": "<script>alert('xss')</script>叶片出现近圆形病斑，周围有黄色晕圈",
            "imageUrl": SELFTEST_IMAGE_URL,
        })
        rid_xss = body["data"]["recordId"]
        records.append(rid_xss)
        # 这里必须检查诊断结果：之前漏检，导致诊断失败时用例静默地"通过"了两条断言
        _, body = call("POST", "/tz/diagnosis/diagnose", params={"recordId": rid_xss, "useLlm": "false"})
        check("含脚本标签的症状也能正常诊断",
              body.get("code") == 200 and (body.get("data") or {}).get("diagnosisName"), body)
        _, body = call("POST", "/tz/report/generate/%d" % rid_xss, params={"useLlm": "false"})
        html = str(((body.get("data") or {}).get("reportText")) or "")
        check("报告已生成（XSS 用例）", bool(html), body)
        check("报告未原样输出脚本标签", "<script>" not in html, html[:200])
        check("脚本标签被转义后原样展示", "&lt;script&gt;" in html, html[:200])

        # --- 8.12 不存在的记录 ---
        _, body = call("POST", "/tz/report/generate/99999999", params={"useLlm": "false"})
        check("对不存在的记录生成报告被拒", body.get("code") == 500, body)
        _, body = call("POST", "/tz/suggestion/generate/99999999", params={"useLlm": "false"})
        check("对不存在的记录生成建议被拒", body.get("code") == 500, body)

    finally:
        # 删记录会级联删掉自动排出的复查任务
        for rid in records:
            call("DELETE", "/tz/record/%d" % rid)
        call("DELETE", "/tz/plot/%d" % plot_id)
    return True


def case_authz():
    section("9. 鉴权边界")
    # 无 token 访问受保护接口。
    # 注意：若依的 AuthenticationEntryPointImpl 走全局异常处理，
    # 返回的是 HTTP 200 + body.code=401，不是 HTTP 401。
    global _token
    saved, _token = _token, None
    status, body = call("GET", "/tz/plot/list")
    _token = saved
    check("无 token 访问被拒（body.code=401）",
          body.get("code") == 401, "status=%s body=%s" % (status, body))
    return True


def case_paging():
    section("10. 分页")
    stamp = str(int(time.time()))[-6:]
    prefix = "分页自测-" + stamp
    for i in range(3):
        call("POST", "/tz/plot", {"plotName": "%s-%d" % (prefix, i), "status": "0"})

    # 新增走的是若依标准 toAjax（不回 data），按唯一的地块名回查 ID
    _, body = call("GET", "/tz/plot/list", params={"plotName": prefix})
    ids = [r["plotId"] for r in body.get("rows", [])]
    ok = check("准备 3 条分页数据", len(ids) == 3, body)
    if not ok:
        return False

    _, body = call("GET", "/tz/plot/list",
                   params={"plotName": prefix, "pageNum": 1, "pageSize": 2})
    check("pageSize=2 只返回 2 行", len(body.get("rows", [])) == 2, body)
    check("total 是命中总数而非当页数", body.get("total") == 3, body)

    _, body2 = call("GET", "/tz/plot/list",
                    params={"plotName": prefix, "pageNum": 2, "pageSize": 2})
    first_ids = {r["plotId"] for r in body.get("rows", [])}
    second_ids = {r["plotId"] for r in body2.get("rows", [])}
    check("第二页返回剩下 1 行", len(second_ids) == 1, body2)
    check("两页数据不重叠", not (first_ids & second_ids), (first_ids, second_ids))

    # 交给第 16 节统一删，不在这里就地删。
    # 就地删是「跑完才删」，本函数前面任何一步 return False 都会把 3 条假数据留在库里；
    # 而且这里过去是 call(...) 之后不看返回值，删失败也没人知道 ——
    # 线上就留下过一条 plot_name='分页自测-...' 的垃圾数据，看板上一直挂着一个假地块。
    _created["pagingPlotIds"] = ids
    return True


def case_qa():
    section("12. 农技问答（P1）")
    stamp = str(int(time.time()))[-6:]

    # 知识库能命中的问题：正常作答。
    # 注意 /tz/qa/ask 收的是 @RequestParam（前端 api/tianzhen/qa.js 用的也是 params），
    # 不是 JSON body —— 这里传错成 body 会得到「question 参数不存在」。
    _, body = call("POST", "/tz/qa/ask",
                   params={"question": "柑橘叶片上出现近圆形病斑、周围有黄色晕圈，是什么病？", "useLlm": False})
    ok = check("提问返回 200", body.get("code") == 200, body)
    if not ok:
        return False
    data = body.get("data") or {}
    session_id = data.get("sessionId")
    _created["qaSessionId"] = session_id
    check("自动建会话并回传 sessionId", bool(session_id), data)
    check("有作答内容", bool((data.get("answer") or "").strip()), data)
    check("标明结果来源（kb/llm 二选一）",
          data.get("source") in ("kb", "llm"), data.get("source"))
    check("knowledgeId 指向命中的知识库条目", bool(data.get("knowledgeId")), data)
    check("必带免责声明", bool((data.get("disclaimer") or "").strip()), data)
    check("声明 useLlm=False 不算降级", data.get("degraded") is False,
          "显式关掉大模型是用户自己的选择，不该记成降级：%s" % data.get("degraded"))

    # 会话标题要截断，否则一条长问题会把会话列表撑爆
    _, body = call("GET", "/tz/qa/session/%d" % session_id)
    title = (body.get("data") or {}).get("sessionTitle") or ""
    check("会话标题非空", bool(title), body.get("data"))
    check("会话标题截断到 30 字以内", len(title) <= 30, "len=%d title=%s" % (len(title), title))

    # 同一会话追问：计数必须累加（验证 SQL 自增，而不是读改写）
    _, body = call("POST", "/tz/qa/ask",
                   params={"question": "那红蜘蛛怎么防治？", "sessionId": session_id, "useLlm": False})
    check("同会话追问复用 sessionId",
          (body.get("data") or {}).get("sessionId") == session_id, body.get("data"))

    _, body = call("GET", "/tz/qa/messages/%d" % session_id)
    msgs = body.get("data") or []
    check("两次提问共 4 条消息（2 问 2 答）", len(msgs) == 4, msgs)
    if len(msgs) == 4:
        check("消息按时间正序（问在答前）",
              [m["role"] for m in msgs] == ["user", "assistant", "user", "assistant"],
              [m.get("role") for m in msgs])
        check("助手消息带 answerStatus", all(m.get("answerStatus") for m in msgs[1::2]),
              [m.get("answerStatus") for m in msgs[1::2]])

    _, body = call("GET", "/tz/qa/session/list", params={"pageNum": 1, "pageSize": 50})
    mine = [r for r in (body.get("rows") or []) if r.get("sessionId") == session_id]
    check("会话列表能查到该会话", len(mine) == 1, body.get("rows"))
    check("msgCount 累加到 4", mine and mine[0].get("msgCount") == 4,
          mine[0].get("msgCount") if mine else None)

    # 知识库完全无关的问题：必须明说没有依据，而不是编一个像样的答案
    _, body = call("POST", "/tz/qa/ask",
                   params={"question": "请问今天股市收盘怎么样，我想买点基金" + stamp, "useLlm": False})
    data = body.get("data") or {}
    check("知识库无匹配时不硬答", data.get("refused") is True or data.get("answerStatus") == "1",
          "should refuse: %s" % data)
    check("无匹配时交代了原因", bool((data.get("sourceText") or "").strip()), data.get("sourceText"))

    # 这条提问刻意不带 sessionId，后端会为它另建一个会话 —— 也得登记，否则每跑一次
    # 就在库里留一条「今天股市收盘怎么样」的会话：演示会话列表里堆着 6 条跟农业无关的
    # 记录，删起来还得先查是哪来的。上面那个会话有级联断言，所以单独走 extras。
    off_topic_sid = data.get("sessionId")
    if off_topic_sid and off_topic_sid != session_id:
        _created.setdefault("extraQaSessionIds", []).append(off_topic_sid)

    # 问到关闭的会话：应被拒
    _, body = call("PUT", "/tz/qa/session/close/%d" % session_id)
    check("关闭会话", body.get("code") == 200, body)
    _, body = call("POST", "/tz/qa/ask",
                   params={"question": "关闭后还能问吗？", "sessionId": session_id, "useLlm": False})
    check("已关闭的会话拒绝继续提问", body.get("code") != 200 or
          (body.get("data") or {}).get("refused") is True, body)

    # 空问题
    _, body = call("POST", "/tz/qa/ask", params={"question": "   ", "useLlm": False})
    check("空问题被拦截", body.get("code") == 500, body)

    return True


def case_market():
    section("13. 行情（P1）")
    stamp = str(int(time.time()))[-6:]
    variety = "自测品种" + stamp

    _, body = call("POST", "/tz/market", {
        "cropType": "柑橘", "variety": variety, "priceType": "1", "region": "自测产区",
        "price": 3.85, "priceUnit": "元/斤", "changeRate": 2.5,
        "priceDate": "2026-09-12", "source": "自测数据，非真实行情",
    })
    check("录入行情", body.get("code") == 200, body)

    _, body = call("GET", "/tz/market/list", params={"variety": variety})
    rows = body.get("rows") or []
    ok = check("按品种查到行情", len(rows) == 1, body)
    if not ok:
        return False
    price_id = rows[0]["priceId"]
    _created["priceId"] = price_id
    check("金额以数值返回", isinstance(rows[0].get("price"), (int, float)), rows[0].get("price"))
    check("来源字段随列表返回", rows[0].get("source") == "自测数据，非真实行情", rows[0].get("source"))

    # 来源必填：说不清出处的行情比没有行情更糟
    _, body = call("POST", "/tz/market", {
        "cropType": "柑橘", "variety": variety + "-x", "price": 1.0, "priceDate": "2026-09-12",
    })
    check("缺数据来源被校验拦截", body.get("code") == 500, body)

    # 修改走完整对象：行情编辑会把来源当必填项校验（不允许把已有来源改空）。
    # 界面上 handleUpdate 也是先 getMarket 读全再整体提交，与此一致。
    _, body = call("GET", "/tz/market/%d" % price_id)
    full = body["data"]
    full["price"] = 4.20
    _, body = call("PUT", "/tz/market", full)
    check("修改行情", body.get("code") == 200, body)
    _, body = call("GET", "/tz/market/%d" % price_id)
    check("修改后价格生效", float(body["data"]["price"]) == 4.20, body.get("data"))
    check("未改动的字段保持不变", body["data"].get("source") == "自测数据，非真实行情", body.get("data"))

    # 已有来源不允许被改成空：来源是这条数据可不可信的全部依据
    _, body = call("PUT", "/tz/market",
                   {"priceId": price_id, "price": 4.20, "source": "", "cropType": "柑橘"})
    check("不允许把来源改成空", body.get("code") == 500, body)

    _, body = call("GET", "/tz/market/%d" % 99999999)
    check("查不存在的行情返回空 data", body.get("data") is None, body)
    return True


def case_supply():
    section("14. 供求撮合（P1）")
    stamp = str(int(time.time()))[-6:]

    _, body = call("POST", "/tz/supply", {
        "infoType": "1", "cropType": "柑橘", "variety": "沃柑" + stamp, "quantity": 30,
        "priceExpect": "面议", "region": "自测产区", "contactName": "自测员",
        "contactPhone": "13800000000", "description": "自测数据", "status": "0",
    })
    check("发布供应信息", body.get("code") == 200, body)

    _, body = call("GET", "/tz/supply/list", params={"variety": "沃柑" + stamp})
    rows = body.get("rows") or []
    ok = check("按品种查到供求", len(rows) == 1, body)
    if not ok:
        return False
    info_id = rows[0]["infoId"]
    _created["infoId"] = info_id
    check("默认状态为已发布", rows[0].get("status") == "0", rows[0].get("status"))

    # 改状态走专门的接口：只传 infoId + status。
    # 走 PUT /tz/supply 会被「品种、联系人、电话不能为空」的完整校验挡回来，
    # 而那几项本次并不打算改 —— 所以状态变更单独开了一个口子。
    _, body = call("PUT", "/tz/supply", {"infoId": info_id, "status": "2"})
    check("走完整校验的接口会被必填项挡回（故需独立 status 接口）",
          body.get("code") == 500, body)

    _, body = call("PUT", "/tz/supply/status", {"infoId": info_id, "status": "2"})
    check("标记成交", body.get("code") == 200, body)
    _, body = call("GET", "/tz/supply/%d" % info_id)
    check("状态已变为已成交", body["data"].get("status") == "2", body.get("data"))
    check("改状态没清掉联系方式", body["data"].get("contactPhone") == "13800000000", body.get("data"))
    check("改状态没清掉数量", float(body["data"].get("quantity")) == 30, body["data"])
    check("改状态没清掉描述", body["data"].get("description") == "自测数据", body["data"])

    # 状态取值必须受控，否则可以把记录改成任意字符串
    _, body = call("PUT", "/tz/supply/status", {"infoId": info_id, "status": "9"})
    check("非法状态被拒绝", body.get("code") == 500, body)
    _, body = call("PUT", "/tz/supply/status", {"status": "0"})
    check("缺 infoId 被拒绝", body.get("code") == 500, body)

    # 下架与重新发布
    _, body = call("PUT", "/tz/supply/status", {"infoId": info_id, "status": "1"})
    check("下架", body.get("code") == 200, body)
    _, body = call("PUT", "/tz/supply/status", {"infoId": info_id, "status": "0"})
    check("重新发布", body.get("code") == 200, body)

    # 按状态过滤
    _, body = call("GET", "/tz/supply/list", params={"status": "0", "variety": "沃柑" + stamp})
    check("按状态过滤生效", len(body.get("rows") or []) == 1, body)

    # 类型与必填校验
    _, body = call("POST", "/tz/supply", {"cropType": "柑橘", "contactName": "自测员",
                                          "contactPhone": "13800000000"})
    check("缺信息类型被校验拦截", body.get("code") == 500, body)

    _, body = call("POST", "/tz/supply", {"infoType": "1", "cropType": "柑橘"})
    check("缺联系人被校验拦截", body.get("code") == 500, body)

    _, body = call("POST", "/tz/supply", {"infoType": "1", "contactName": "自测员",
                                          "contactPhone": "13800000000"})
    check("缺品种被校验拦截", body.get("code") == 500, body)

    # 一条留不下联系方式的供求信息，对撮合毫无用处，所以后端也要拦住（前端校验可被绕过）
    _, body = call("POST", "/tz/supply", {"infoType": "1", "cropType": "柑橘",
                                          "contactName": "自测员"})
    check("缺联系电话被校验拦截", body.get("code") == 500, body)

    return True


def case_perms():
    """
    权限串与菜单行的一致性巡检。

    写一个没有对应菜单行的 @PreAuthorize，管理员（*:*:*）照样能过、其他角色一律 403 ——
    是那种「我这能用你那不能用」、最难排查的故障。这条用例把它变成一次可回归的检查。
    """
    section("15. 权限串与菜单行一致")
    import os
    import re

    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "backend")
    pattern = re.compile(r"hasPermi\('([^']+)'\)")
    declared = set()
    scanned = 0
    for dirpath, _, filenames in os.walk(root):
        if "target" in dirpath.split(os.sep):
            continue
        for fn in filenames:
            if not fn.endswith(".java"):
                continue
            with open(os.path.join(dirpath, fn), encoding="utf-8") as f:
                text = f.read()
            found = pattern.findall(text)
            if found:
                scanned += 1
                declared.update(found)

    tz_perms = {p for p in declared if p.startswith("tz:")}
    check("扫描到业务权限串", len(tz_perms) > 0 and scanned > 0,
          "scanned_files=%d perms=%d" % (scanned, len(tz_perms)))

    # 注意：/system/menu/list 返回的是树（body.data 里的 children 嵌套），不是分页的 rows。
    # 一开始按 rows 取，结果拿到空集合、把 42 条权限全报成缺失，白排查一轮。
    granted = set()

    def walk(nodes):
        for node in nodes or []:
            if node.get("perms"):
                granted.add(node["perms"])
            walk(node.get("children"))

    _, body = call("GET", "/system/menu/list")
    walk(body.get("data"))
    check("读到菜单权限集合", len(granted) > 0, "granted=%d" % len(granted))

    missing = sorted(p for p in tz_perms if p not in granted)
    check("每个业务权限串都有对应菜单行", not missing,
          "缺菜单行（非 admin 会 403）：%s" % missing)

    return True


def case_cleanup():
    section("16. 清理自测数据")
    plot_id = _created.get("plotId")
    record_id = _created.get("recordId")

    # P1 的三张表：会话删除要级联带走消息，先确认消息真没了再删别的
    if _created.get("qaSessionId"):
        sid = _created["qaSessionId"]
        _, body = call("GET", "/tz/qa/messages/%d" % sid)
        before = len(body.get("data") or [])
        _, body = call("DELETE", "/tz/qa/session/%d" % sid)
        check("删除会话（级联删消息）", body.get("code") == 200, body)
        _, body = call("GET", "/tz/qa/messages/%d" % sid)
        check("会话删除后消息一并清掉", len(body.get("data") or []) == 0,
              "删除前 %d 条，删除后 %d 条" % (before, len(body.get("data") or [])))

    # 「无匹配提问」那条自己开的会话，原因见 case_qa
    for extra_sid in _created.get("extraQaSessionIds") or []:
        _, body = call("DELETE", "/tz/qa/session/%d" % extra_sid)
        check("删除无匹配提问产生的会话", body.get("code") == 200, body)

    # 第 10 节造的分页假数据。放在这里删（而不是那节末尾就地删）的原因见 case_paging。
    paging_ids = _created.get("pagingPlotIds") or []
    if paging_ids:
        deleted = 0
        for pid in paging_ids:
            _, body = call("DELETE", "/tz/plot/%d" % pid)
            if body.get("code") == 200:
                deleted += 1
        check("删除分页自测地块 %d 条" % len(paging_ids),
              deleted == len(paging_ids), "实际删掉 %d 条" % deleted)

    if _created.get("priceId"):
        _, body = call("DELETE", "/tz/market/%d" % _created["priceId"])
        check("删除自测行情", body.get("code") == 200, body)
    if _created.get("infoId"):
        _, body = call("DELETE", "/tz/supply/%d" % _created["infoId"])
        check("删除自测供求", body.get("code") == 200, body)

    if record_id:
        _, body = call("DELETE", "/tz/record/%d" % record_id)
        check("删除巡田记录（级联删复查任务）", body.get("code") == 200, body)
    if plot_id:
        _, body = call("DELETE", "/tz/plot/%d" % plot_id)
        check("删除自测地块", body.get("code") == 200, body)
    return True


# ---------------------------------------------------------------- 主流程

CASES = [
    ("login", case_login),
    ("plot", case_plot),
    ("record", case_record),
    ("followup", case_followup),
    ("knowledge", case_knowledge),
    ("dashboard", case_dashboard),
    ("diagnosis", case_diagnosis),
    ("describe", case_describe),
    ("closure", case_closure),
    ("authz", case_authz),
    ("paging", case_paging),
    ("qa", case_qa),
    ("market", case_market),
    ("supply", case_supply),
    ("perms", case_perms),
    ("cleanup", case_cleanup),
]


def main():
    keyword = sys.argv[1].lower() if len(sys.argv) > 1 else None
    print("田诊助手 接口自测 -> %s" % BASE)
    started = time.time()

    for name, fn in CASES:
        if keyword and keyword not in name:
            continue
        try:
            fn()
        except Exception as e:
            check("%s 用例异常" % name, False, "%s: %s" % (type(e).__name__, e))

    passed = sum(1 for _, ok, _ in _results if ok)
    failed = [r for r in _results if not r[1]]
    print("\n" + "=" * 62)
    print("合计 %d 项，通过 %d，失败 %d，用时 %.1fs" %
          (len(_results), passed, len(failed), time.time() - started))
    if failed:
        print("\n失败项：")
        for name, _, detail in failed:
            print("  - %s\n      %s" % (name, detail))
    print("=" * 62)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
