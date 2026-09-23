package com.ruoyi.web.controller.ssk;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.vo.BorrowRecordVo;
import com.ruoyi.system.biz.ssk.BorrowRecordBiz;

/**
 * 借阅记录管理
 *
 * <p>本模块只提供「查看借阅信息」与「还书」两类能力，不提供借阅记录的修改、删除与单条详情：
 * 借阅记录属于业务凭证，一旦产生就不应被改写或抹除；列表已经把一条记录的全部字段展示完整，
 * 前端没有单条详情的使用场景，故不保留无人调用的 /{id} 接口。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/ssk/borrowRecord")
public class BorrowRecordController extends BaseController
{
    @Autowired
    private BorrowRecordBiz borrowRecordBiz;

    /**
     * 分页查询借阅记录列表
     *
     * <p>分页参数与囚号条件从查询串绑定到实体上；书籍名称与借阅状态都不是
     * ssk_borrow_record 的表字段，单独作为请求参数接收。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRecord:list')")
    @GetMapping("/list")
    public TableDataInfo list(BorrowRecord record,
            @RequestParam(value = "bookName", required = false) String bookName,
            @RequestParam(value = "status", required = false) String status)
    {
        startPage();
        List<BorrowRecordVo> list = borrowRecordBiz.findPage(record, bookName, status);
        return getDataTable(list);
    }

    /**
     * 借出弹窗的书籍下拉数据
     *
     * <p>返回全部未删除的书籍（含库存），库存为 0 的书由前端置灰，
     * 因此该接口与「借出」按钮使用同一权限标识。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRecord:borrow')")
    @GetMapping("/bookOptions")
    public AjaxResult bookOptions()
    {
        return success(borrowRecordBiz.findBookOptions());
    }

    /**
     * 借出图书（新增借阅记录）
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRecord:borrow')")
    @Log(title = "借阅记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody BorrowRecord record)
    {
        return toAjax(borrowRecordBiz.create(record));
    }

    /**
     * 归还图书
     *
     * <p>实际归还时间由后端固定取当前系统时间，不接受前端传入。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:borrowRecord:return')")
    @Log(title = "借阅记录", businessType = BusinessType.UPDATE)
    @PutMapping("/return/{id}")
    public AjaxResult returnBook(@PathVariable("id") Integer id)
    {
        return toAjax(borrowRecordBiz.returnBook(id));
    }
}
