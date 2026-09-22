package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BookCategory;

/**
 * 图书分类 数据层
 *
 * <p>方法命名遵循项目规范：create、findByXxx、updateByXxx、deleteByXxx，方法名不再重复携带模块名。</p>
 *
 * @author ruoyi
 */
public interface BookCategoryMapper
{
    /**
     * 查询全部未删除的图书分类
     *
     * @return 图书分类集合（按上级ID、显示排序、主键升序）
     */
    public List<BookCategory> findList();

    /**
     * 根据主键查询图书分类
     *
     * @param id 主键
     * @return 图书分类信息
     */
    public BookCategory findById(Integer id);

    /**
     * 统计同层级下同名类目数量，用于名称唯一性校验
     *
     * @param title     类目名称
     * @param parentId  上级ID（为空表示顶级）
     * @param excludeId 需要排除的类目ID（修改时排除自身）
     * @return 同名类目数量
     */
    public int countByTitle(@Param("title") String title, @Param("parentId") Integer parentId, @Param("excludeId") Integer excludeId);

    /**
     * 新增图书分类
     *
     * @param bookCategory 图书分类信息
     * @return 结果
     */
    public int create(BookCategory bookCategory);

    /**
     * 修改图书分类
     *
     * @param bookCategory 图书分类信息
     * @return 结果
     */
    public int updateById(BookCategory bookCategory);

    /**
     * 批量更新显示排序
     *
     * @param categories 仅携带 id 与 orderNum 的类目集合
     * @param updatedBy  操作人ID
     * @param updatedAt  操作时间
     * @return 结果
     */
    public int updateSortByIds(@Param("categories") List<BookCategory> categories, @Param("updatedBy") Integer updatedBy, @Param("updatedAt") Date updatedAt);

    /**
     * 批量逻辑删除图书分类
     *
     * @param ids       需要删除的类目ID数组
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    public int deleteByIds(@Param("ids") Integer[] ids, @Param("updatedBy") Integer updatedBy, @Param("updatedAt") Date updatedAt);
}
