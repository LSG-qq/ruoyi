package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.BookCategory;

/**
 * 图书分类 服务层
 *
 * <p>本接口仅承载简单的增删查改，业务规则（唯一性校验、层级校验、批量删除拦截等）统一放在 biz 层处理。</p>
 *
 * @author ruoyi
 */
public interface IBookCategoryService
{
    /**
     * 查询全部未删除的图书分类
     *
     * @return 图书分类集合
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
     * 统计同层级下同名类目数量
     *
     * @param title     类目名称
     * @param parentId  上级ID
     * @param excludeId 需要排除的类目ID
     * @return 同名类目数量
     */
    public int countByTitle(String title, Integer parentId, Integer excludeId);

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
    public int updateSortByIds(List<BookCategory> categories, Integer updatedBy, Date updatedAt);

    /**
     * 批量逻辑删除图书分类
     *
     * @param ids       需要删除的类目ID数组
     * @param updatedBy 操作人ID
     * @param updatedAt 操作时间
     * @return 结果
     */
    public int deleteByIds(Integer[] ids, Integer updatedBy, Date updatedAt);
}
