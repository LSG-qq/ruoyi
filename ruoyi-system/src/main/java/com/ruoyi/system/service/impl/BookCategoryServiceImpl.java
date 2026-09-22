package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.mapper.BookCategoryMapper;
import com.ruoyi.system.service.IBookCategoryService;

/**
 * 图书分类 服务层实现
 *
 * @author ruoyi
 */
@Service
public class BookCategoryServiceImpl implements IBookCategoryService
{
    @Autowired
    private BookCategoryMapper bookCategoryMapper;

    /**
     * 查询全部未删除的图书分类
     *
     * @return 图书分类集合
     */
    @Override
    public List<BookCategory> findList()
    {
        return bookCategoryMapper.findList();
    }

    /**
     * 根据主键查询图书分类
     *
     * @param id 主键
     * @return 图书分类信息
     */
    @Override
    public BookCategory findById(Integer id)
    {
        return bookCategoryMapper.findById(id);
    }

    /**
     * 统计同层级下同名类目数量
     *
     * @param title     类目名称
     * @param parentId  上级ID
     * @param excludeId 需要排除的类目ID
     * @return 同名类目数量
     */
    @Override
    public int countByTitle(String title, Integer parentId, Integer excludeId)
    {
        return bookCategoryMapper.countByTitle(title, parentId, excludeId);
    }

    /**
     * 新增图书分类
     *
     * @param bookCategory 图书分类信息
     * @return 结果
     */
    @Override
    public int create(BookCategory bookCategory)
    {
        return bookCategoryMapper.create(bookCategory);
    }

    /**
     * 修改图书分类
     *
     * @param bookCategory 图书分类信息
     * @return 结果
     */
    @Override
    public int updateById(BookCategory bookCategory)
    {
        return bookCategoryMapper.updateById(bookCategory);
    }

    /**
     * 批量更新显示排序
     *
     * @param categories 仅携带 id 与 orderNum 的类目集合
     * @param updatedBy  操作人ID
     * @param updatedAt  操作时间
     * @return 结果
     */
    @Override
    public int updateSortByIds(List<BookCategory> categories, Integer updatedBy, Date updatedAt)
    {
        return bookCategoryMapper.updateSortByIds(categories, updatedBy, updatedAt);
    }

    /**
     * 批量逻辑删除图书分类
     *
     * @param ids       需要删除的类目ID数组
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    @Override
    public int deleteByIds(Integer[] ids, Integer updatedBy, Date updatedAt)
    {
        return bookCategoryMapper.deleteByIds(ids, updatedBy, updatedAt);
    }
}
