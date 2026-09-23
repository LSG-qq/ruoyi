package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.vo.BookVo;

/**
 * 图书 服务层
 *
 * <p>本层只做简单的增删查改，业务编排统一放在 ruoyi-admin 的 BookBiz 中。</p>
 *
 * @author ruoyi
 */
public interface IBookService
{
    /**
     * 按条件分页查询图书
     *
     * @param book           查询条件（书籍名称、作者、书架号）
     * @param categoryIdList 类目ID集合（含选中类目及其全部下级类目），为空表示不按类目过滤
     * @return 图书集合
     */
    public List<BookVo> findPage(Book book, List<Integer> categoryIdList);

    /**
     * 查询全部未删除图书，仅返回借阅下拉所需字段
     *
     * @return 图书集合（仅 id、name、author、shelfCode、stockQuantity 有值）
     */
    public List<BookVo> findOptionList();

    /**
     * 根据主键查询图书
     *
     * @param id 主键
     * @return 图书信息
     */
    public BookVo findById(Integer id);

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return 结果
     */
    public int create(Book book);

    /**
     * 修改图书
     *
     * @param book 图书信息
     * @return 结果
     */
    public int updateById(Book book);

    /**
     * 批量逻辑删除图书
     *
     * @param ids       需要删除的图书ID数组
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    public int deleteByIds(Integer[] ids, Integer updatedBy, Date updatedAt);

    /**
     * 按主键调整图书库存，借出与归还共用
     *
     * @param id        书籍ID
     * @param delta     库存增量，借出传 -1，归还传 1
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果，0 表示库存不足或书籍不存在
     */
    public int updateStockById(Integer id, int delta, Integer updatedBy, Date updatedAt);
}
