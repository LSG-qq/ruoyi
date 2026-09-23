package com.ruoyi.system.domain.vo;

/**
 * 图书展示对象（终端囚犯可见字段）
 *
 * <p>终端（ruoyi-client）的图书浏览接口用它返回数据。之所以不复用后台的 {@link BookVo}：
 * 那张表上有 created_by / created_at / updated_by / updated_at / deleted 五个审计与状态字段，
 * 它们是内部运营信息 —— 囚犯既用不到「谁录入的这本书」，也不该看到逻辑删除标记。
 * 直接把 BookVo 返给终端会让这些字段出现在响应体里（未查询时为 null，仍然看得见字段名），
 * 因此这里按终端实际需要做一次字段裁剪。</p>
 *
 * <p>本类只做字段投影，不增加任何表以外的字段，因此不违反「实体类不增加表以外的字段」
 * 这条规范 —— 它是 {@code Book} 的子集视图，不是它的扩展。</p>
 *
 * <p>故意不继承 {@code Book}：继承会连带把父类的审计字段一起带进响应体，
 * 那就失去了裁剪的意义。</p>
 *
 * @author ruoyi
 */
public class ClientBookVo
{
    /** 主键ID */
    private Integer id;

    /** 书籍名称 */
    private String name;

    /** 书籍描述 */
    private String description;

    /** 作者 */
    private String author;

    /** 类目ID集，多个类目以英文逗号分隔，如 "3,7,12"；终端用类目树把取值还原成名称 */
    private String categoryIds;

    /** 书架号，囚犯凭它到架上找书；取值来自字典 book_shelfs */
    private String shelfCode;

    /** 封面，取图片集的第一张图 */
    private String cover;

    /** 图片集，多张图片以英文逗号分隔，最多 5 张 */
    private String images;

    /** 可借库存，为 0 时终端把「申请借阅」按钮置灰 */
    private Integer stockQuantity;

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

    public Integer getStockQuantity()
    {
        return stockQuantity;
    }

    public void setStockQuantity(Integer stockQuantity)
    {
        this.stockQuantity = stockQuantity;
    }
}
