package com.ruoyi.system.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 图书对象 ssk_book
 *
 * <p>字段严格对应数据库表 ssk_book，不额外增加表以外的属性；
 * 列表展示所需的创建人/更新人姓名由 {@link com.ruoyi.system.domain.vo.BookVo} 承载。</p>
 *
 * <p>字段与表列的对应关系如下（14 个字段对 14 列，一个不多一个不少）：</p>
 * <pre>
 *   id            -> id             主键
 *   name          -> name           书籍名称
 *   description   -> description    简介
 *   stockQuantity -> stock_quantity 可借库存，只由借出/归还按增量维护，编辑图书不改它
 *   author        -> author         作者
 *   categoryIds   -> category_ids   类目ID集，多个类目以英文逗号拼接，如 "3,7,12"
 *   shelfCode     -> shelf_code     书架号，取值来自字典 book_shelfs
 *   cover         -> cover          封面，后端由 images 的第一张派生，不接受前端传值
 *   images        -> images         图片集，英文逗号分隔，最多 5 张
 *   createdBy     -> created_by     创建者用户ID（int，不是用户名）
 *   createdAt     -> created_at     创建时间
 *   updatedBy     -> updated_by     更新者用户ID
 *   updatedAt     -> updated_at     更新时间
 *   deleted       -> deleted        逻辑删除标记，false 未删除 / true 已删除
 * </pre>
 *
 * <p>本表不继承若依内置的 BaseEntity：审计字段名为 created_by / created_at / updated_by / updated_at，
 * 且 created_by 存的是用户ID（int），与 BaseEntity 的 create_by（varchar 用户名）不一致。</p>
 *
 * @author ruoyi
 */
public class Book
{
    /** 主键ID */
    private Integer id;

    /** 书籍名称 */
    @NotBlank(message = "书籍名称不能为空")
    @Size(max = 255, message = "书籍名称长度不能超过255个字符")
    private String name;

    /** 书籍描述 */
    @NotBlank(message = "书籍描述不能为空")
    @Size(max = 500, message = "书籍描述长度不能超过500个字符")
    private String description;

    /**
     * 库存，默认 1
     *
     * <p>本实体同时用于新增与修改，实体上的校验注解对两个操作**同时**生效，而库存只在新增时有意义
     * （修改图书完全不碰库存，由借出/归还维护），所以这里刻意不加 {@code @NotNull} 与 {@code @Min}：
     * 加了会让「修改图书」因为一个它根本不用、也不提交的字段被参数校验拦下。</p>
     *
     * <p>库存的校验全部收在 {@code BookFieldRules.normalizeStockQuantity}（只被新增流程调用）：
     * 为空落默认库存 1，负数抛「库存不能小于0」。前端另外用 {@code el-input-number :min="0"} 做界面层限制。</p>
     */
    private Integer stockQuantity;

    /** 作者 */
    @NotBlank(message = "作者不能为空")
    @Size(max = 255, message = "作者长度不能超过255个字符")
    private String author;

    /** 类目ID集，多个类目以英文逗号分隔，如 "3,7,12" */
    @NotBlank(message = "类目不能为空")
    @Size(max = 255, message = "类目ID集长度不能超过255个字符")
    private String categoryIds;

    /** 书架号，取自字典 book_shelfs */
    @NotBlank(message = "书架号不能为空")
    @Size(max = 255, message = "书架号长度不能超过255个字符")
    private String shelfCode;

    /** 封面，取图片集的第一张图，由后端根据 images 派生 */
    @Size(max = 255, message = "封面长度不能超过255个字符")
    private String cover;

    /** 图片集，多张图片以英文逗号分隔，最多 5 张 */
    @Size(max = 500, message = "图片集长度不能超过500个字符")
    private String images;

    /** 创建人ID */
    private Integer createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新人ID */
    private Integer updatedBy;

    /** 更新时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date updatedAt;

    /** 是否已删除：false未删除，true已删除 */
    private Boolean deleted;

    public Integer getId()
    {
        return id;
    }

    public void setId(Integer id)
    {
        this.id = id;
    }

    public String getName()
    {
        return name;
    }

    public void setName(String name)
    {
        this.name = name;
    }

    public String getDescription()
    {
        return description;
    }

    public void setDescription(String description)
    {
        this.description = description;
    }

    public Integer getStockQuantity()
    {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity)
    {
        this.stockQuantity = stockQuantity;
    }

    public String getAuthor()
    {
        return author;
    }

    public void setAuthor(String author)
    {
        this.author = author;
    }

    public String getCategoryIds()
    {
        return categoryIds;
    }

    public void setCategoryIds(String categoryIds)
    {
        this.categoryIds = categoryIds;
    }

    public String getShelfCode()
    {
        return shelfCode;
    }

    public void setShelfCode(String shelfCode)
    {
        this.shelfCode = shelfCode;
    }

    public String getCover()
    {
        return cover;
    }

    public void setCover(String cover)
    {
        this.cover = cover;
    }

    public String getImages()
    {
        return images;
    }

    public void setImages(String images)
    {
        this.images = images;
    }

    public Integer getCreatedBy()
    {
        return createdBy;
    }

    public void setCreatedBy(Integer createdBy)
    {
        this.createdBy = createdBy;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Integer getUpdatedBy()
    {
        return updatedBy;
    }

    public void setUpdatedBy(Integer updatedBy)
    {
        this.updatedBy = updatedBy;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }

    public Boolean getDeleted()
    {
        return deleted;
    }

    public void setDeleted(Boolean deleted)
    {
        this.deleted = deleted;
    }
}
