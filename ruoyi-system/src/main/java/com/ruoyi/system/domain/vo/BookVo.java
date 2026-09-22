package com.ruoyi.system.domain.vo;

import com.ruoyi.system.domain.Book;

/**
 * 图书展示对象
 *
 * <p>ssk_book 表中 created_by / updated_by 只存用户ID，列表需要展示创建人、更新人姓名，
 * 按项目规范「实体类不增加表以外的字段」，这些关联查询出来的字段统一放在 VO 中。</p>
 *
 * @author ruoyi
 */
public class BookVo extends Book
{
    /** 创建人姓名（sys_user.nick_name） */
    private String createdByName;

    /** 更新人姓名（sys_user.nick_name） */
    private String updatedByName;

    public String getCreatedByName()
    {
        return createdByName;
    }

    public void setCreatedByName(String createdByName)
    {
        this.createdByName = createdByName;
    }

    public String getUpdatedByName()
    {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName)
    {
        this.updatedByName = updatedByName;
    }
}
