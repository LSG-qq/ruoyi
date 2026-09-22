package com.ruoyi.web.biz.ssk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.service.IBookCategoryService;

/**
 * 图书分类 业务层
 *
 * <p>承接 controller 与 service 之间的业务编排，包含以下规则：</p>
 * <ul>
 *     <li>顶级类目的上级ID统一归一化为 null，与表结构的 parent_id 默认值保持一致；</li>
 *     <li>同一上级下类目名称不允许重复；</li>
 *     <li>上级类目不允许选择自身或自身的下级，避免出现环形层级；</li>
 *     <li>删除为逻辑删除，勾选的类目会连同其全部下级一起删除（级联），前端已就级联影响做过二次确认；</li>
 *     <li>排序接口只接受 id 与 orderNum，避免被用来修改类目名称、上级等其它字段。</li>
 * </ul>
 *
 * @author ruoyi
 */
@Component
public class BookCategoryBiz
{
    /** 类目名称最大长度，与 ssk_book_category.title 字段长度保持一致 */
    private static final int TITLE_MAX_LENGTH = 50;

    /** 备注最大长度，与 ssk_book_category.remark 字段长度保持一致 */
    private static final int REMARK_MAX_LENGTH = 255;

    @Autowired
    private IBookCategoryService bookCategoryService;

    /**
     * 查询全部图书类目（一次性返回，不做分页与条件过滤）
     *
     * @return 图书类目集合，前端据此组装树结构
     */
    public List<BookCategory> findList()
    {
        return bookCategoryService.findList();
    }

    /**
     * 查询图书类目，并排除自身及其全部下级
     *
     * <p>用于「编辑」弹窗中的上级类目下拉，避免把节点挂到自己的下边。</p>
     *
     * @param id 需要排除的类目ID
     * @return 可作为上级的类目集合
     */
    public List<BookCategory> findListExcludeSelf(Integer id)
    {
        List<BookCategory> all = bookCategoryService.findList();
        if (id == null)
        {
            return all;
        }
        Set<Integer> excludeIds = collectSelfAndDescendantIds(all, id);
        List<BookCategory> result = new ArrayList<>(all.size());
        for (BookCategory category : all)
        {
            if (!excludeIds.contains(category.getId()))
            {
                result.add(category);
            }
        }
        return result;
    }

    /**
     * 根据主键查询图书类目，不存在时抛出业务异常
     *
     * @param id 主键
     * @return 图书类目信息
     */
    public BookCategory findById(Integer id)
    {
        return requireCategory(id);
    }

    /**
     * 新增图书类目
     *
     * @param bookCategory 图书类目信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(BookCategory bookCategory)
    {
        Integer parentId = normalizeParentId(bookCategory.getParentId());
        // 上级类目必须真实存在，防止前端传入非法ID造成脏数据
        if (parentId != null)
        {
            requireCategory(parentId);
        }
        String title = normalizeTitle(bookCategory.getTitle());
        if (bookCategoryService.countByTitle(title, parentId, null) > 0)
        {
            throw new ServiceException("新增类目'" + title + "'失败，同级下已存在同名类目");
        }
        bookCategory.setParentId(parentId);
        bookCategory.setOrderNum(normalizeOrderNum(bookCategory.getOrderNum()));
        bookCategory.setTitle(title);
        bookCategory.setRemark(normalizeRemark(bookCategory.getRemark()));
        bookCategory.setCreatedBy(currentUserId());
        bookCategory.setCreatedAt(new Date());
        // 新增时清空审计字段与逻辑删除标记，全部由后端生成
        bookCategory.setUpdatedBy(null);
        bookCategory.setUpdatedAt(null);
        bookCategory.setDeleted(null);
        return bookCategoryService.create(bookCategory);
    }

    /**
     * 修改图书类目
     *
     * @param bookCategory 图书类目信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateById(BookCategory bookCategory)
    {
        Integer id = bookCategory.getId();
        if (id == null)
        {
            throw new ServiceException("图书类目ID不能为空");
        }
        requireCategory(id);
        String title = normalizeTitle(bookCategory.getTitle());
        Integer parentId = normalizeParentId(bookCategory.getParentId());
        // 层级合法性校验：不能选自己，也不能选自己的下级
        if (id.equals(parentId))
        {
            throw new ServiceException("上级类目不能选择当前类目自身");
        }
        if (parentId != null)
        {
            requireCategory(parentId);
            if (collectSelfAndDescendantIds(bookCategoryService.findList(), id).contains(parentId))
            {
                throw new ServiceException("上级类目不能选择当前类目的下级类目");
            }
        }
        if (bookCategoryService.countByTitle(title, parentId, id) > 0)
        {
            throw new ServiceException("修改类目'" + title + "'失败，同级下已存在同名类目");
        }
        bookCategory.setParentId(parentId);
        bookCategory.setOrderNum(normalizeOrderNum(bookCategory.getOrderNum()));
        bookCategory.setTitle(title);
        bookCategory.setRemark(normalizeRemark(bookCategory.getRemark()));
        // 创建信息不允许被前端覆盖
        bookCategory.setCreatedBy(null);
        bookCategory.setCreatedAt(null);
        bookCategory.setDeleted(null);
        bookCategory.setUpdatedBy(currentUserId());
        bookCategory.setUpdatedAt(new Date());
        return bookCategoryService.updateById(bookCategory);
    }

    /**
     * 批量保存显示排序
     *
     * <p>前端只提交发生变更的节点，这里重新构造仅含 id 与 orderNum 的载体，
     * 避免排序接口被用来修改类目名称、上级等其它字段。</p>
     *
     * @param categories 待更新的排序信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateSort(List<BookCategory> categories)
    {
        if (categories == null || categories.isEmpty())
        {
            throw new ServiceException("未检测到排序变更");
        }
        List<BookCategory> changed = new ArrayList<>(categories.size());
        for (BookCategory item : categories)
        {
            if (item == null || item.getId() == null)
            {
                continue;
            }
            BookCategory target = new BookCategory();
            target.setId(item.getId());
            target.setOrderNum(normalizeOrderNum(item.getOrderNum()));
            changed.add(target);
        }
        if (changed.isEmpty())
        {
            throw new ServiceException("未检测到排序变更");
        }
        return bookCategoryService.updateSortByIds(changed, currentUserId(), new Date());
    }

    /**
     * 批量删除图书类目（逻辑删除，级联删除下级）
     *
     * <p>勾选的类目会连同其全部下级一起逻辑删除，前端已就级联影响做过二次确认。
     * 勾选了上级又勾选其下级时，由集合天然完成去重，不会重复更新。</p>
     *
     * @param ids 需要删除的类目ID数组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Integer[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的图书类目");
        }
        List<BookCategory> all = bookCategoryService.findList();
        Map<Integer, BookCategory> categoryMap = new HashMap<>(all.size());
        for (BookCategory category : all)
        {
            categoryMap.put(category.getId(), category);
        }
        Set<Integer> deleteIds = new HashSet<>();
        for (Integer id : ids)
        {
            if (id == null)
            {
                continue;
            }
            if (!categoryMap.containsKey(id))
            {
                throw new ServiceException("图书类目不存在或已被删除，请刷新后重试");
            }
            deleteIds.addAll(collectSelfAndDescendantIds(all, id));
        }
        if (deleteIds.isEmpty())
        {
            throw new ServiceException("请选择要删除的图书类目");
        }
        return bookCategoryService.deleteByIds(deleteIds.toArray(new Integer[0]), currentUserId(), new Date());
    }

    /**
     * 查询类目，不存在时抛出业务异常
     *
     * @param id 类目ID
     * @return 图书类目信息
     */
    private BookCategory requireCategory(Integer id)
    {
        if (id == null)
        {
            throw new ServiceException("图书类目ID不能为空");
        }
        BookCategory category = bookCategoryService.findById(id);
        if (category == null)
        {
            throw new ServiceException("图书类目不存在或已被删除");
        }
        return category;
    }

    /**
     * 归一化上级ID：null 与 0 都视为顶级类目，统一存为 null
     *
     * @param parentId 上级ID
     * @return 归一化后的上级ID
     */
    private Integer normalizeParentId(Integer parentId)
    {
        if (parentId == null || parentId.intValue() == 0)
        {
            return null;
        }
        return parentId;
    }

    /**
     * 校验并返回规范化的类目名称
     *
     * @param title 类目名称
     * @return 去除首尾空格后的类目名称
     */
    private String normalizeTitle(String title)
    {
        String trimmed = StringUtils.trim(title);
        if (StringUtils.isEmpty(trimmed))
        {
            throw new ServiceException("类目名称不能为空");
        }
        if (trimmed.length() > TITLE_MAX_LENGTH)
        {
            throw new ServiceException("类目名称长度不能超过" + TITLE_MAX_LENGTH + "个字符");
        }
        return trimmed;
    }

    /**
     * 校验并返回规范化的备注，空字符串统一存为 null
     *
     * @param remark 备注内容
     * @return 处理后的备注
     */
    private String normalizeRemark(String remark)
    {
        String trimmed = StringUtils.trim(remark);
        if (trimmed.length() > REMARK_MAX_LENGTH)
        {
            throw new ServiceException("备注长度不能超过" + REMARK_MAX_LENGTH + "个字符");
        }
        return StringUtils.isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 校验并返回规范化的显示排序，空值按 0 处理
     *
     * @param orderNum 显示排序
     * @return 归一化后的显示排序
     */
    private Integer normalizeOrderNum(Integer orderNum)
    {
        if (orderNum == null)
        {
            return 0;
        }
        if (orderNum.intValue() < 0)
        {
            throw new ServiceException("显示排序不能小于0");
        }
        return orderNum;
    }

    /**
     * 获取当前登录用户ID，用于填充创建人/更新人
     *
     * @return 当前登录用户ID
     */
    private Integer currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("获取当前登录用户信息失败");
        }
        return userId.intValue();
    }

    /**
     * 收集指定类目及其全部子孙类目的ID
     *
     * <p>类目为一次性全量加载，直接在内存中逐层展开，避免递归查询数据库。</p>
     *
     * @param all    全部类目
     * @param rootId 根类目ID
     * @return 自身及全部子孙类目ID集合
     */
    private Set<Integer> collectSelfAndDescendantIds(List<BookCategory> all, Integer rootId)
    {
        Set<Integer> result = new HashSet<>();
        if (rootId == null)
        {
            return result;
        }
        result.add(rootId);
        boolean expanded = true;
        while (expanded)
        {
            expanded = false;
            for (BookCategory category : all)
            {
                if (category.getParentId() != null && result.contains(category.getParentId())
                        && result.add(category.getId()))
                {
                    expanded = true;
                }
            }
        }
        return result;
    }
}
