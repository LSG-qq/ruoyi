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

    /** 类目标题 */
    @NotBlank(message = "类目名称不能为空")
    @Size(max = 50, message = "类目名称长度不能超过50个字符")
    private String title;

    /** 备注 */
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
