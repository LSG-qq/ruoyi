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
}
