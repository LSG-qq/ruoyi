package com.ruoyi.web.biz.ssk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.service.IBookCategoryService;
import com.ruoyi.system.service.IBookService;

/**
 * 图书 业务层
 *
 * <p>承接 controller 与 service 之间的业务编排，包含以下规则：</p>
 * <ul>
 *     <li>查询条件为空时不参与过滤，类目筛选为「选中类目 + 其全部下级类目」；</li>
 *     <li>类目ID集统一去重、去空格后按英文逗号拼接，并校验类目确实存在；</li>
 *     <li>图片集最多 5 张，统一去重去空格后按英文逗号拼接，封面固定取第一张图；</li>
 *     <li>创建人、创建时间、更新人、更新时间、逻辑删除标记全部由后端生成，不接受前端传入。</li>
 * </ul>
 *
 * @author ruoyi
 */
@Component
public class BookBiz
{
    /** 书籍名称最大长度，与 ssk_book.name 字段长度保持一致 */
    private static final int NAME_MAX_LENGTH = 255;

    /** 书籍描述最大长度，与 ssk_book.description 字段长度保持一致 */
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    /** 作者最大长度，与 ssk_book.author 字段长度保持一致 */
    private static final int AUTHOR_MAX_LENGTH = 255;

    /** 类目ID集最大长度，与 ssk_book.category_ids 字段长度保持一致 */
    private static final int CATEGORY_IDS_MAX_LENGTH = 255;

    /** 书架号最大长度，与 ssk_book.shelf_code 字段长度保持一致 */
    private static final int SHELF_CODE_MAX_LENGTH = 255;

    /** 图片集最大长度，与 ssk_book.images 字段长度保持一致 */
    private static final int IMAGES_MAX_LENGTH = 500;

    /** 图片集最多张数 */
    private static final int IMAGES_MAX_COUNT = 5;

    /** 默认库存 */
    private static final int DEFAULT_STOCK_QUANTITY = 1;

    @Autowired
    private IBookService bookService;

    @Autowired
    private IBookCategoryService bookCategoryService;

    /**
     * 按条件分页查询图书
     *
     * @param book       查询条件（书籍名称、作者、书架号），为空表示不带条件
     * @param categoryId 左侧类目树选中的类目ID，为空或 0 表示「全部」
     * @return 图书集合
     */
    public List<BookVo> findPage(Book book, Integer categoryId)
    {
        Book query = new Book();
        if (book != null)
        {
            // 查询条件统一去空格，空串按未填写处理
            query.setName(normalizeQueryText(book.getName()));
            query.setAuthor(normalizeQueryText(book.getAuthor()));
            query.setShelfCode(normalizeQueryText(book.getShelfCode()));
        }
        return bookService.findPage(query, resolveCategoryIds(categoryId));
    }

    /**
     * 根据主键查询图书，不存在时抛出业务异常
     *
     * @param id 主键
     * @return 图书信息
     */
    public BookVo findById(Integer id)
    {
        return requireBook(id);
    }

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(Book book)
    {
        if (book == null)
        {
            throw new ServiceException("图书信息不能为空");
        }
        applyEditableFields(book, bookCategoryService.findList());
        // 主键与审计字段一律由后端生成
        book.setId(null);
        book.setCreatedBy(currentUserId());
        book.setCreatedAt(new Date());
        book.setUpdatedBy(null);
        book.setUpdatedAt(null);
        book.setDeleted(null);
        return bookService.create(book);
    }

    /**
     * 修改图书
     *
     * @param book 图书信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateById(Book book)
    {
        if (book == null)
        {
            throw new ServiceException("图书信息不能为空");
        }
        requireBook(book.getId());
        applyEditableFields(book, bookCategoryService.findList());
        // 创建信息与逻辑删除标记不允许被前端覆盖
        book.setCreatedBy(null);
        book.setCreatedAt(null);
        book.setDeleted(null);
        book.setUpdatedBy(currentUserId());
        book.setUpdatedAt(new Date());
        return bookService.updateById(book);
    }

    /**
     * 批量删除图书（逻辑删除）
     *
     * <p>删除是幂等的：已被删除的图书再次提交不会报错，也不会影响其它数据。</p>
     *
     * @param ids 需要删除的图书ID数组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Integer[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的图书");
        }
        Set<Integer> targetIds = new LinkedHashSet<>();
        for (Integer id : ids)
        {
            if (id != null)
            {
                targetIds.add(id);
            }
        }
        if (targetIds.isEmpty())
        {
            throw new ServiceException("请选择要删除的图书");
        }
        return bookService.deleteByIds(targetIds.toArray(new Integer[0]), currentUserId(), new Date());
    }

    /**
     * 处理可编辑字段：校验、去空格、格式化类目与图片集，并派生封面
     *
     * @param book       图书信息，方法内直接修改该对象
     * @param categories 全部未删除的类目，用于校验类目ID是否合法
     */
    private void applyEditableFields(Book book, List<BookCategory> categories)
    {
        book.setName(requireText(book.getName(), NAME_MAX_LENGTH, "书籍名称"));
        book.setDescription(requireText(book.getDescription(), DESCRIPTION_MAX_LENGTH, "书籍描述"));
        book.setAuthor(requireText(book.getAuthor(), AUTHOR_MAX_LENGTH, "作者"));
        book.setShelfCode(requireText(book.getShelfCode(), SHELF_CODE_MAX_LENGTH, "书架号"));
        book.setStockQuantity(normalizeStockQuantity(book.getStockQuantity()));
        book.setCategoryIds(normalizeCategoryIds(book.getCategoryIds(), categories));
        String images = normalizeImages(book.getImages());
        book.setImages(images);
        // 封面固定为图片集的第一张图，空图片集时封面置空
        book.setCover(firstImage(images));
    }

    /**
     * 解析左侧类目树选中的类目ID，展开为「自身 + 全部下级」的ID集合
     *
     * @param categoryId 选中的类目ID，为空或 0 表示「全部」
     * @return 类目ID集合，返回 null 表示不按类目过滤
     */
    private List<Integer> resolveCategoryIds(Integer categoryId)
    {
        if (categoryId == null || categoryId.intValue() <= 0)
        {
            return null;
        }
        List<BookCategory> all = bookCategoryService.findList();
        Set<Integer> ids = collectSelfAndDescendantIds(all, categoryId);
        return new ArrayList<>(ids);
    }

    /**
     * 校验并返回规范化的类目ID集，多个类目按英文逗号拼接
     *
     * @param categoryIds 前端提交的类目ID集
     * @param categories  全部未删除的类目
     * @return 规范化后的类目ID集
     */
    private String normalizeCategoryIds(String categoryIds, List<BookCategory> categories)
    {
        Set<Integer> existingIds = new HashSet<>();
        for (BookCategory category : categories)
        {
            existingIds.add(category.getId());
        }
        Set<Integer> selectedIds = new LinkedHashSet<>();
        for (String part : split(categoryIds))
        {
            Integer categoryId = parseCategoryId(part);
            if (!existingIds.contains(categoryId))
            {
                throw new ServiceException("所选类目不存在或已被删除，请重新选择");
            }
            selectedIds.add(categoryId);
        }
        if (selectedIds.isEmpty())
        {
            throw new ServiceException("请至少选择一个类目");
        }
        String joined = StringUtils.join(selectedIds, ",");
        if (joined.length() > CATEGORY_IDS_MAX_LENGTH)
        {
            throw new ServiceException("所选类目过多，类目ID集长度不能超过" + CATEGORY_IDS_MAX_LENGTH + "个字符");
        }
        return joined;
    }

    /**
     * 校验并返回规范化的图片集，最多 {@link #IMAGES_MAX_COUNT} 张
     *
     * @param images 前端提交的图片集
     * @return 规范化后的图片集，无图片时返回 null
     */
    private String normalizeImages(String images)
    {
        Set<String> selected = new LinkedHashSet<>(split(images));
        if (selected.size() > IMAGES_MAX_COUNT)
        {
            throw new ServiceException("图片集最多上传" + IMAGES_MAX_COUNT + "张图片");
        }
        String joined = StringUtils.join(selected, ",");
        if (joined.length() > IMAGES_MAX_LENGTH)
        {
            throw new ServiceException("图片集长度不能超过" + IMAGES_MAX_LENGTH + "个字符");
        }
        return StringUtils.isEmpty(joined) ? null : joined;
    }

    /**
     * 取图片集中的第一张图作为封面
     *
     * @param images 规范化后的图片集
     * @return 封面地址，无图片时返回 null
     */
    private String firstImage(String images)
    {
        List<String> parts = split(images);
        return parts.isEmpty() ? null : parts.get(0);
    }

    /**
     * 校验并返回必填文本字段
     *
     * @param value     字段值
     * @param maxLength 字段最大长度
     * @param label     字段中文名，用于提示信息
     * @return 去除首尾空格后的字段值
     */
    private String requireText(String value, int maxLength, String label)
    {
        String trimmed = StringUtils.trim(value);
        if (StringUtils.isEmpty(trimmed))
        {
            throw new ServiceException(label + "不能为空");
        }
        if (trimmed.length() > maxLength)
        {
            throw new ServiceException(label + "长度不能超过" + maxLength + "个字符");
        }
        return trimmed;
    }

    /**
     * 查询条件去空格，空串按未填写处理
     *
     * @param value 条件值
     * @return 处理后的条件值，未填写时返回 null
     */
    private String normalizeQueryText(String value)
    {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 校验并返回规范化的库存，空值按默认库存处理
     *
     * @param stockQuantity 库存
     * @return 归一化后的库存
     */
    private Integer normalizeStockQuantity(Integer stockQuantity)
    {
        if (stockQuantity == null)
        {
            return DEFAULT_STOCK_QUANTITY;
        }
        if (stockQuantity.intValue() < 0)
        {
            throw new ServiceException("库存不能小于0");
        }
        return stockQuantity;
    }

    /**
     * 按英文逗号拆分并去掉空串与首尾空格
     *
     * @param value 待拆分文本
     * @return 拆分结果
     */
    private List<String> split(String value)
    {
        List<String> result = new ArrayList<>();
        if (StringUtils.isEmpty(value))
        {
            return result;
        }
        for (String part : value.split(","))
        {
            String trimmed = StringUtils.trim(part);
            if (StringUtils.isNotEmpty(trimmed))
            {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 把类目ID文本解析为整数
     *
     * @param text 类目ID文本
     * @return 类目ID
     */
    private Integer parseCategoryId(String text)
    {
        try
        {
            return Integer.valueOf(text);
        }
        catch (NumberFormatException e)
        {
            throw new ServiceException("类目ID格式不正确：" + text);
        }
    }

    /**
     * 查询图书，不存在时抛出业务异常
     *
     * @param id 图书ID
     * @return 图书信息
     */
    private BookVo requireBook(Integer id)
    {
        if (id == null)
        {
            throw new ServiceException("图书ID不能为空");
        }
        BookVo book = bookService.findById(id);
        if (book == null)
        {
            throw new ServiceException("图书不存在或已被删除");
        }
        return book;
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
