package com.ruoyi.system.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 图书分类对象 ssk_book_category
 *
 * <p>字段严格对应数据库表 ssk_book_category，不额外增加表以外的属性；
 * 前端展示树形结构所需的 children 由前端 handleTree 组装，不在此实体中承载。</p>
 *
 * <p>字段与表列的对应关系如下（10 个字段对 10 列，一个不多一个不少）：</p>
 * <pre>
 *   id        -> id         主键
 *   parentId  -> parent_id  上级类目ID，NULL 表示顶级类目（业务层把 null 与 0 都归一化为 NULL）
 *   orderNum  -> order_num  显示排序，同级内升序，值越小越靠前
 *   title     -> title      类目标题，同一上级下不允许重名
 *   remark    -> remark     备注，允许为空，也允许被清空
 *   createdBy -> created_by 创建者用户ID（int，不是用户名）
 *   createdAt -> created_at 创建时间
 *   updatedBy -> updated_by 更新者用户ID
 *   updatedAt -> updated_at 更新时间
 *   deleted   -> deleted    逻辑删除标记，false 未删除 / true 已删除
 * </pre>
 *
 * <p>本表不继承若依内置的 BaseEntity：审计字段名为 created_by / created_at / updated_by / updated_at，
 * 且 created_by 存的是用户ID（int），与 BaseEntity 的 create_by（varchar 用户名）不一致。
 * 这一点与 Book 保持一致。</p>
 *
 * <p>删除一律是逻辑删除（deleted 置 1），所有查询都带 deleted = 0，
 * 因此已删除类目不会出现在树里，历史数据仍可追溯。</p>
 *
 * @author ruoyi
 */
public class BookCategory
{
    /** 主键ID */
    private Integer id;

    /** 上级ID，为空表示顶级类目 */
    private Integer parentId;

    /** 显示排序，同级内升序排列，值越小越靠前 */
    private Integer orderNum;

    /**
     * 类目标题
     *
     * <p>「非空 + 长度」这两个约束对新增和修改都成立（两个操作都要提交名称），
     * 所以可以直接放在实体上由 {@code @Validated} 统一拦截；
     * 业务层 {@code BookCategoryBiz.normalizeTitle} 会再做一次去空格与长度校验作为兜底。</p>
     *
     * <p>「同一上级下不允许重名」这类跨行校验不在这里做，它需要查库，属于业务层职责。</p>
     */
    @NotBlank(message = "类目名称不能为空")
    @Size(max = 50, message = "类目名称长度不能超过50个字符")
    private String title;

    /** 备注，允许为空；修改时该列无条件更新，因此传空即可清空备注 */
    @Size(max = 255, message = "备注长度不能超过255个字符")
    private String remark;

    /** 创建人（用户ID） */
    private Integer createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新人（用户ID） */
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

    public Integer getParentId()
    {
        return parentId;
    }

    public void setParentId(Integer parentId)
    {
        this.parentId = parentId;
    }

    public Integer getOrderNum()
    {
        return orderNum;
    }

    public void setOrderNum(Integer orderNum)
    {
        this.orderNum = orderNum;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getRemark()
    {
        return remark;
    }

    public void setRemark(String remark)
    {
        this.remark = remark;
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
