package com.ruoyi.web.controller.tianzhen;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.ai.QaAnswerResult;
import com.ruoyi.system.ai.QaService;
import com.ruoyi.system.domain.TzQaMessage;
import com.ruoyi.system.domain.TzQaSession;
import com.ruoyi.system.service.ITzQaSessionService;

/**
 * 农技问答 信息操作处理
 *
 * 注意权限串只用到 tz:qa:list / query / add / remove 四个 ——
 * 菜单表里只为它们建了 F 行。写一个没有对应菜单行的权限串，
 * 管理员能过、其他角色一律 403，是最难排查的一类「我这能用你那不能用」。
 *
 * @author tianzhen
 */
@RestController
@RequestMapping("/tz/qa")
public class TzQaController extends BaseController
{
    @Autowired
    private QaService qaService;

    @Autowired
    private ITzQaSessionService tzQaSessionService;

    /**
     * 提问并取得回答。
     *
     * @param question  问题原文
     * @param sessionId 会话ID，为空则新建会话（便于农户直接开口问，不用先建会话）
     * @param useLlm    本次是否允许调用大模型
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:add')")
    @Log(title = "农技问答", businessType = BusinessType.OTHER)
    @PostMapping("/ask")
    public AjaxResult ask(@RequestParam String question,
                          @RequestParam(required = false) Long sessionId,
                          @RequestParam(defaultValue = "true") boolean useLlm)
    {
        return success(qaService.ask(sessionId, question, useLlm));
    }

    /**
     * 读取会话的完整问答记录（只读，不重新作答）
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:query')")
    @GetMapping("/messages/{sessionId}")
    public AjaxResult messages(@PathVariable Long sessionId)
    {
        List<TzQaMessage> list = qaService.listMessages(sessionId);
        return success(list);
    }

    /**
     * 查询会话列表
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:list')")
    @GetMapping("/session/list")
    public TableDataInfo sessionList(TzQaSession tzQaSession)
    {
        startPage();
        List<TzQaSession> list = tzQaSessionService.selectTzQaSessionList(tzQaSession);
        return getDataTable(list);
    }

    /**
     * 获取会话详细信息
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:query')")
    @GetMapping(value = "/session/{sessionId}")
    public AjaxResult getSession(@PathVariable Long sessionId)
    {
        return success(tzQaSessionService.selectTzQaSessionById(sessionId));
    }

    /**
     * 关闭会话（保留记录，只是不再接受追问）
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:remove')")
    @Log(title = "农技问答", businessType = BusinessType.UPDATE)
    @PutMapping("/session/close/{sessionId}")
    public AjaxResult closeSession(@PathVariable Long sessionId)
    {
        TzQaSession session = new TzQaSession();
        session.setSessionId(sessionId);
        session.setStatus("1");
        session.setUpdateBy(getUsername());
        return toAjax(tzQaSessionService.updateTzQaSession(session));
    }

    /**
     * 删除会话（连同其下全部消息）
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:remove')")
    @Log(title = "农技问答", businessType = BusinessType.DELETE)
    @DeleteMapping("/session/{sessionIds}")
    public AjaxResult removeSession(@PathVariable Long[] sessionIds)
    {
        return toAjax(tzQaSessionService.deleteTzQaSessionByIds(sessionIds));
    }

    /**
     * 手工补录一条会话（脚本自测与数据修复用）
     */
    @PreAuthorize("@ss.hasPermi('tz:qa:add')")
    @Log(title = "农技问答", businessType = BusinessType.INSERT)
    @PostMapping("/session")
    public AjaxResult addSession(@Validated @RequestBody TzQaSession tzQaSession)
    {
        if (tzQaSession.getStatus() == null)
        {
            tzQaSession.setStatus("0");
        }
        tzQaSession.setCreateBy(getUsername());
        return toAjax(tzQaSessionService.insertTzQaSession(tzQaSession));
    }
}
