package com.ruoyi.client.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.biz.ssk.BorrowRequestBiz;

/**
 * 终端 - 借阅申请
 *
 * <p>囚犯在终端上能做的只有两件事：提交申请、查看自己的申请。
 * 因此这里没有修改与删除，也没有「同意/拒绝」——那属于管理后台的职责。</p>
 *
 * <p><b>囚号一律取不到入参里。</b>提交申请时由后端从登录令牌解析囚号，
 * 查询我的申请时同样由后端从令牌取，接口不提供任何能指定「查谁的数据」的参数。
 * 这是刻意的设计：一旦接受囚号入参，终端只要改一个参数就能替他人提交申请、
 * 或翻看别人的申请记录。</p>
 *
 * <p>接口一律不挂 {@code @PreAuthorize}（囚犯角色没有权限位），也不写操作日志：
 * 「谁在什么时候提交了申请」由申请行本身记录（prisoner_number + created_at），
 * 那才是这条业务事实的权威来源，再往 sys_oper_log 里抄一份只会产生重复且可能不一致的记录。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/client/borrowRequest")
public class ClientBorrowRequestController extends BaseController
{
    @Autowired
    private BorrowRequestBiz borrowRequestBiz;

    /**
     * 提交借阅申请
     *
     * @param body 请求体，只含 bookId 与 borrowDays
     * @return 结果
     */
    @PostMapping("/create")
    public AjaxResult create(@RequestBody CreateBody body)
    {
        Integer bookId = body == null ? null : body.getBookId();
        Integer borrowDays = body == null ? null : body.getBorrowDays();
        return toAjax(borrowRequestBiz.create(bookId, borrowDays));
    }

    /**
     * 分页查询我的借阅申请
     *
     * <p>囚号由后端从登录令牌取得，前端无从指定查询对象。</p>
     *
     * @param status 申请状态，为空表示不筛选
     * @return 分页结果
     */
    @GetMapping("/myList")
    public TableDataInfo myList(@RequestParam(value = "status", required = false) String status)
    {
        startPage();
        return getDataTable(borrowRequestBiz.findMyPage(status));
    }

    /**
     * 申请状态下拉选项
     *
     * <p>取值与标签由后端状态枚举派生，与列表里显示的状态是同一份数据来源，
     * 前端不自己维护一份状态字典。</p>
     *
     * @return 状态下拉选项
     */
    @GetMapping("/statusOptions")
    public AjaxResult statusOptions()
    {
        return success(borrowRequestBiz.findStatusOptions());
    }

    /**
     * 借阅天数的默认值与上下限
     *
     * <p>提交申请的弹窗要用它渲染输入框的默认值、最小值与最大值。
     * 这些数字只在后端定义一次、由接口下发，前端不写死，
     * 避免出现「界面允许填 400 天、提交后被后端拒绝」这种前后矛盾。</p>
     *
     * @return 借阅天数的默认值与上下限
     */
    @GetMapping("/borrowDayLimits")
    public AjaxResult borrowDayLimits()
    {
        return success(borrowRequestBiz.findBorrowDayLimits());
    }

    /**
     * 提交申请的请求体
     *
     * <p>刻意只提供这两个字段。囚号、申请时间、申请状态都由后端从登录令牌与系统时间取得，
     * 请求体里**根本没有**这些字段，前端也就无从传入、无从伪造
     * —— 这比「提供字段但后端覆盖」更彻底：后者一旦哪天漏了覆盖就是越权。</p>
     */
    public static class CreateBody
    {
        /** 书籍ID */
        private Integer bookId;

        /** 借阅天数，为空时后端按默认天数处理 */
        private Integer borrowDays;

        public Integer getBookId()
        {
            return bookId;
        }

        public void setBookId(Integer bookId)
        {
            this.bookId = bookId;
        }

        public Integer getBorrowDays()
        {
            return borrowDays;
        }

        public void setBorrowDays(Integer borrowDays)
        {
            this.borrowDays = borrowDays;
        }
    }
}
