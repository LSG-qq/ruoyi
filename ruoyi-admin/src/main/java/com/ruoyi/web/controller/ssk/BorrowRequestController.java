package com.ruoyi.web.controller.ssk;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.biz.ssk.BorrowRequestBiz;

/**
 * 借阅申请管理
 *
 * <p>申请由囚犯在终端提交（终端直接写库），后台只负责「查看」与「审核」，
 * 因此本模块不提供新增、修改、删除与单条详情接口：
 * 列表已经把一条申请的全部字段展示完整，前端没有单条详情的使用场景，
 * 故不保留无人调用的 /{id} 查询接口。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/ssk/borrowRequest")
public class BorrowRequestController extends BaseController
{
    @Autowired
    private BorrowRequestBiz borrowRequestBiz;

    /**
     * 分页查询借阅申请列表
     *
     * <p>书籍名称与状态都不是 ssk_borrow_request 的表字段（书名来自 ssk_book），
     * 无法绑定到实体上，因此单独作为请求参数接收。
     * 状态的默认值「待处理」由前端指定，后端不做隐式默认，不传即表示不带状态条件。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRequest:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(value = "bookName", required = false) String bookName,
            @RequestParam(value = "status", required = false) String status)
    {
        startPage();
        return getDataTable(borrowRequestBiz.findPage(bookName, status));
    }

    /**
     * 申请状态下拉选项
     *
     * <p>选项由后端状态枚举派生，页面下拉与列表标签都用这一份数据，
     * 避免「枚举改了取值、前端还写着旧值」这类只在特定操作下才暴露的问题。
     * 该接口是页面渲染的组成部分，因此与列表共用查看权限，不额外开权限位。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRequest:list')")
    @GetMapping("/statusOptions")
    public AjaxResult statusOptions()
    {
        return success(borrowRequestBiz.findStatusOptions());
    }

    /**
     * 借阅天数的默认值与上下限
     *
     * <p>同意弹窗的输入框需要这个默认值、最小值与最大值。与 {@link #statusOptions()} 同理，
     * 这些常量由后端统一定义、统一下发，前端不再写一份数字靠人工对齐，
     * 避免「界面允许填的天数被后端拒绝」这种前后矛盾。
     * 属于页面渲染的组成部分，因此同样共用查看权限，不额外开权限位。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRequest:list')")
    @GetMapping("/borrowDayLimits")
    public AjaxResult borrowDayLimits()
    {
        return success(borrowRequestBiz.findBorrowDayLimits());
    }

    /**
     * 同意申请（同意即借出）
     *
     * <p>只收「借阅天数」一个可选参数，为空时后端按默认 7 天计算应还时间。
     * 借阅人不再是入参 —— 囚号已由终端写在申请行里（prisoner_number），
     * 审核环节只决定「批不批」、不决定「借给谁」，否则会出现「申请人与借阅人对不上」
     * 且事后无从追查。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRequest:approve')")
    @Log(title = "借阅申请", businessType = BusinessType.UPDATE)
    @PutMapping("/approve/{id}")
    public AjaxResult approve(@PathVariable("id") Integer id,
            @RequestParam(value = "borrowDays", required = false) Integer borrowDays)
    {
        return toAjax(borrowRequestBiz.approve(id, borrowDays));
    }

    /**
     * 拒绝申请
     *
     * <p>拒绝只改状态，不产生借阅记录，也不影响书籍库存。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRequest:reject')")
    @Log(title = "借阅申请", businessType = BusinessType.UPDATE)
    @PutMapping("/reject/{id}")
    public AjaxResult reject(@PathVariable("id") Integer id)
    {
        return toAjax(borrowRequestBiz.reject(id));
    }
}
