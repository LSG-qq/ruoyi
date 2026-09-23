package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.mapper.BookMapper;
import com.ruoyi.system.service.IBookService;

/**
 * 图书 服务层实现
 *
 * @author ruoyi
 */
@Service
public class BookServiceImpl implements IBookService
{
    @Autowired
    private BookMapper bookMapper;

    /**
     * 按条件分页查询图书
     *
     * @param book           查询条件（书籍名称、作者、书架号）
     * @param categoryIdList 类目ID集合（含选中类目及其全部下级类目）
     * @return 图书集合
     */
    @Override
    public List<BookVo> findPage(Book book, List<Integer> categoryIdList)
    {
        return bookMapper.findPage(book, categoryIdList);
    }

    /**
     * 查询全部未删除图书，仅返回借阅下拉所需字段
     *
     * @return 图书集合
     */
    @Override
    public List<BookVo> findOptionList()
    {
        return bookMapper.findOptionList();
    }

    /**
     * 根据主键查询图书
     *
     * @param id 主键
     * @return 图书信息
     */
    @Override
    public BookVo findById(Integer id)
    {
        return bookMapper.findById(id);
    }

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return 结果
     */
    @Override
    public int create(Book book)
    {
        return bookMapper.create(book);
    }

    /**
     * 修改图书
     *
     * @param book 图书信息
     * @return 结果
     */
    @Override
    public int updateById(Book book)
    {
        return bookMapper.updateById(book);
    }

    /**
     * 批量逻辑删除图书
     *
     * @param ids       需要删除的图书ID数组
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    @Override
    public int deleteByIds(Integer[] ids, Integer updatedBy, Date updatedAt)
    {
        return bookMapper.deleteByIds(ids, updatedBy, updatedAt);
    }

    /**
     * 按主键调整图书库存，借出与归还共用
     *
     * @param id        书籍ID
     * @param delta     库存增量，借出传 -1，归还传 1
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    @Override
    public int updateStockById(Integer id, int delta, Integer updatedBy, Date updatedAt)
    {
        return bookMapper.updateStockById(id, delta, updatedBy, updatedAt);
    }
}
