package com.ruoyi.client.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.biz.ssk.BorrowRecordBiz;

/**
 * 终端 - 我的借阅
 *
 * <p>囚犯只能查看「自己借了哪些书、什么时候该还」，因此这里只有查询接口，
 * 没有借出与归还 —— 那两步由管理员在后台操作。</p>
 *
 * <p>囚号由后端从登录令牌取得，接口不接受任何能指定查询对象的参数；
 * 底层走的是按囚号**等值**匹配的查询，不复用后台列表的模糊匹配
 * （否则囚号 20260001 会命中 20260011，等于看到别人的借阅记录）。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/client/borrowRecord")
public class ClientBorrowRecordController extends BaseController
{
    @Autowired
    private BorrowRecordBiz borrowRecordBiz;

    /**
     * 分页查询我的借阅记录
     *
     * @param status 借阅状态（borrowing / overdue / returned），为空表示不筛选
     * @return 分页结果
     */
    @GetMapping("/myList")
    public TableDataInfo myList(@RequestParam(value = "status", required = false) String status)
    {
        startPage();
        return getDataTable(borrowRecordBiz.findMyPage(status));
    }

    /**
     * 借阅状态下拉选项
     *
     * <p>取值与标签同后端计算借阅状态时用的是同一组常量，前端不自己维护状态字典。</p>
     *
     * @return 状态下拉选项
     */
    @GetMapping("/statusOptions")
    public AjaxResult statusOptions()
    {
        return success(borrowRecordBiz.findStatusOptions());
    }
}
