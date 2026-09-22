package com.ruoyi.web.controller.ssk;

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
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.web.biz.ssk.BookBiz;

/**
 * 图书管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/ssk/book")
public class BookController extends BaseController
{
    @Autowired
    private BookBiz bookBiz;

    /**
     * 分页查询图书列表
     *
     * <p>分页参数与查询条件一并从查询串传入：书籍名称 name、作者 author、书架号 shelfCode
     * 直接绑定到实体上，类目筛选条件 categoryId 为表以外的字段，单独作为请求参数接收。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:book:list')")
    @GetMapping("/list")
    public TableDataInfo list(Book book, @RequestParam(value = "categoryId", required = false) Integer categoryId)
    {
        startPage();
        List<BookVo> list = bookBiz.findPage(book, categoryId);
        return getDataTable(list);
    }

    /**
     * 根据图书编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('ssk:book:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Integer id)
    {
        return success(bookBiz.findById(id));
    }

    /**
     * 新增图书
     */
    @PreAuthorize("@ss.hasPermi('ssk:book:add')")
    @Log(title = "图书管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody Book book)
    {
        return toAjax(bookBiz.create(book));
    }

    /**
     * 修改图书
     */
    @PreAuthorize("@ss.hasPermi('ssk:book:edit')")
    @Log(title = "图书管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody Book book)
    {
        return toAjax(bookBiz.updateById(book));
    }

    /**
     * 删除图书，支持批量删除（图书编号以英文逗号分隔）
     */
    @PreAuthorize("@ss.hasPermi('ssk:book:remove')")
    @Log(title = "图书管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable("ids") Integer[] ids)
    {
        return toAjax(bookBiz.deleteByIds(ids));
    }
}
