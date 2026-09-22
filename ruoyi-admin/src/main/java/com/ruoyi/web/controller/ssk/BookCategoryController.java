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
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.web.biz.ssk.BookCategoryBiz;

/**
 * 图书类目管理
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/ssk/bookCategory")
public class BookCategoryController extends BaseController
{
    @Autowired
    private BookCategoryBiz bookCategoryBiz;

    /**
     * 查询全部图书类目（一次性全量返回，前端自行组装树结构）
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:list')")
    @GetMapping("/list")
    public AjaxResult list()
    {
        return success(bookCategoryBiz.findList());
    }

    /**
     * 查询图书类目列表（排除自身及下级），用于编辑时选择上级类目
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:list')")
    @GetMapping("/list/exclude/{id}")
    public AjaxResult excludeSelf(@PathVariable("id") Integer id)
    {
        return success(bookCategoryBiz.findListExcludeSelf(id));
    }

    /**
     * 根据类目编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable("id") Integer id)
    {
        return success(bookCategoryBiz.findById(id));
    }

    /**
     * 新增图书类目
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:add')")
    @Log(title = "图书类目", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody BookCategory bookCategory)
    {
        return toAjax(bookCategoryBiz.create(bookCategory));
    }

    /**
     * 修改图书类目
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:edit')")
    @Log(title = "图书类目", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody BookCategory bookCategory)
    {
        return toAjax(bookCategoryBiz.updateById(bookCategory));
    }

    /**
     * 保存图书类目排序，请求体为仅含 id 与 orderNum 的数组
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:edit')")
    @Log(title = "图书类目", businessType = BusinessType.UPDATE)
    @PutMapping("/updateSort")
    public AjaxResult updateSort(@RequestBody List<BookCategory> categories)
    {
        return toAjax(bookCategoryBiz.updateSort(categories));
    }

    /**
     * 删除图书类目，支持批量删除（类目编号以英文逗号分隔）
     *
     * <p>删除为级联逻辑删除：勾选的类目会连同其全部下级一起删除。</p>
     */
    @PreAuthorize("@ss.hasPermi('ssk:bookCategory:remove')")
    @Log(title = "图书类目", businessType = BusinessType.DELETE)
    @DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable("ids") Integer[] ids)
    {
        return toAjax(bookCategoryBiz.deleteByIds(ids));
    }
}
