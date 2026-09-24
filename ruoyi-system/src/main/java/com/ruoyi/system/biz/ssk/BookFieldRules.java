package com.ruoyi.system.biz.ssk;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BookCategory;

/**
 * 图书字段的规范化与校验规则
 *
 * <p>从 {@link BookBiz} 中抽出的**纯函数**集合：这里的每个方法都只依赖入参，
 * 不查数据库、不取登录态、不写库，因此可以脱离 Spring 上下文直接单测。</p>
 *
 * <p>抽出它们的原因有两条：</p>
 * <ul>
 *     <li>{@code BookBiz} 的编排职责与「字段怎么规范化」这一层混在一起时，
 *         类行数会突破工程规范里的 500 行上限（agents.md server 段）；</li>
 *     <li>字段规则（最大长度、默认库存、图片张数、类目ID格式）是图书模块里
 *         变动最频繁、也最值得被单独验证的部分，集中一处便于改动与排查。</li>
 * </ul>
 *
 * <p>本类只做搬运，**拆分前后行为完全一致**：异常类型、提示文案、边界取值
 * （空串按未填写处理、空图片集置 null、空库存落默认值）都没有任何调整。
 * 需要带数据库或登录态的校验（如「类目是否真实存在」之外的图书存在性判断）
 * 仍留在 {@link BookBiz}，不在这里补。</p>
 *
 * @author ruoyi
 */
public final class BookFieldRules
{
    /** 书籍名称最大长度，与 ssk_book.name 字段长度保持一致 */
    public static final int NAME_MAX_LENGTH = 255;

    /** 书籍描述最大长度，与 ssk_book.description 字段长度保持一致 */
    public static final int DESCRIPTION_MAX_LENGTH = 500;

    /** 作者最大长度，与 ssk_book.author 字段长度保持一致 */
    public static final int AUTHOR_MAX_LENGTH = 255;

    /** 类目ID集最大长度，与 ssk_book.category_ids 字段长度保持一致 */
    public static final int CATEGORY_IDS_MAX_LENGTH = 255;

    /** 书架号最大长度，与 ssk_book.shelf_code 字段长度保持一致 */
    public static final int SHELF_CODE_MAX_LENGTH = 255;

    /** 图片集最大长度，与 ssk_book.images 字段长度保持一致 */
    public static final int IMAGES_MAX_LENGTH = 500;

    /** 图片集最多张数 */
    public static final int IMAGES_MAX_COUNT = 5;

    /** 默认库存 */
    public static final int DEFAULT_STOCK_QUANTITY = 1;

    /**
     * 工具类不允许实例化
     *
     * <p>声明出来是为了让「本类只有静态方法」这件事成为编译期约束，
     * 而不是靠约定 —— 有人误加一个实例方法时会先在评审里被发现。</p>
     */
    private BookFieldRules()
    {
    }

    /**
     * 校验并返回必填文本字段
     *
     * @param value     字段值
     * @param maxLength 字段最大长度
     * @param label     字段中文名，用于提示信息
     * @return 去除首尾空格后的字段值
     */
    public static String requireText(String value, int maxLength, String label)
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
     * <p>空串必须归一成 null 再交给 SQL：否则「书名包含空串」会命中全部记录，
     * 用户清空搜索框后得到的结果与「没搜索」看起来一样，但走的是两条不同的 SQL。</p>
     *
     * @param value 条件值
     * @return 处理后的条件值，未填写时返回 null
     */
    public static String normalizeQueryText(String value)
    {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 校验并返回规范化的库存，空值按默认库存处理
     *
     * <p>只被新增流程调用：图书创建时必须落一个初始库存，负数在这里被拒绝。
     * 修改流程完全不碰库存（库存在 SQL 的 SET 里用 {@code <if>} 排除），
     * 因此这条「下限」约束只对新增成立，放在业务侧而不是实体注解上。</p>
     *
     * @param stockQuantity 新增时提交的初始库存
     * @return 归一化后的库存
     */
    public static Integer normalizeStockQuantity(Integer stockQuantity)
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
     * 校验并返回规范化的类目ID集，多个类目按英文逗号拼接
     *
     * <p>逐项校验类目确实存在，是为了让「前端传了已被删除的类目ID」得到可读提示，
     * 而不是悄悄存进去、之后列表里显示不出类目名。</p>
     *
     * @param categoryIds 前端提交的类目ID集
     * @param categories  全部未删除的类目
     * @return 规范化后的类目ID集
     */
    public static String normalizeCategoryIds(String categoryIds, List<BookCategory> categories)
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
     * <p>用 {@link LinkedHashSet} 去重且保持提交顺序 —— 顺序有意义：
     * 封面固定取第一张（见 {@link #firstImage}），重复图片会让封面位置错乱。</p>
     *
     * @param images 前端提交的图片集
     * @return 规范化后的图片集，无图片时返回 null
     */
    public static String normalizeImages(String images)
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
    public static String firstImage(String images)
    {
        List<String> parts = split(images);
        return parts.isEmpty() ? null : parts.get(0);
    }

    /**
     * 按英文逗号拆分并去掉空串与首尾空格
     *
     * @param value 待拆分文本
     * @return 拆分结果
     */
    private static List<String> split(String value)
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
    private static Integer parseCategoryId(String text)
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
}
