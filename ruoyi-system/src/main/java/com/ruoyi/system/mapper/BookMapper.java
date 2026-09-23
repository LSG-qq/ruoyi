package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.vo.BookVo;

/**
 * 图书 数据层
 *
 * <p>方法命名遵循项目规范：create、findByXxx、updateByXxx、deleteByXxx，方法名不再重复携带模块名。</p>
 *
 * @author ruoyi
 */
public interface BookMapper
{
    /**
     * 按条件分页查询图书
     *
     * @param book           查询条件（书籍名称、作者、书架号），未填写的条件不参与过滤
     * @param categoryIdList 类目ID集合（含选中类目及其全部下级类目），为空表示不按类目过滤
     * @return 图书集合（含创建人、更新人姓名）
     */
    public List<BookVo> findPage(@Param("book") Book book, @Param("categoryIdList") List<Integer> categoryIdList);

    /**
     * 查询全部未删除图书，仅返回借阅下拉所需字段
     *
     * <p>借出弹窗的书籍下拉只需要 id、名称、作者、书架号与库存，
     * 不需要简介与图片集（简介是长文本、图片集是路径串），
     * 因此这里单独出一条轻量语句，避免把全量图书的明细字段都传给前端。</p>
     *
     * @return 图书集合（仅 id、name、author、shelfCode、stockQuantity 有值）
     */
    public List<BookVo> findOptionList();

    /**
     * 根据主键查询图书
     *
     * @param id 主键
     * @return 图书信息（含创建人、更新人姓名）
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
     * <p>{@code stockQuantity} 为空时不更新 stock_quantity 列。图书修改流程不参与库存维护
     * （库存由借出/归还经 {@link #updateStockById} 按增量变动），因此正常调用不会改动库存。</p>
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
    public int deleteByIds(@Param("ids") Integer[] ids, @Param("updatedBy") Integer updatedBy, @Param("updatedAt") Date updatedAt);

    /**
     * 按主键调整图书库存，借出与归还共用
     *
     * <p>扣减库存（delta 为负数）时 SQL 内自带「不得扣成负数」与「书籍未删除」两个条件，
     * 影响行数为 0 即表示库存不足，由调用方据此提示。归还回补库存（delta 为正数）时
     * 不限制书籍的删除状态，避免书籍被删后归还操作把库存丢掉。</p>
     *
     * @param id        书籍ID
     * @param delta     库存增量，借出传 -1，归还传 1
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果，0 表示条件不满足（如库存不足、书籍不存在）
     */
    public int updateStockById(@Param("id") Integer id, @Param("delta") int delta,
            @Param("updatedBy") Integer updatedBy, @Param("updatedAt") Date updatedAt);
}
