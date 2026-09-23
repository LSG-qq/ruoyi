package com.ruoyi.system.domain;

import java.util.Date;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 借阅记录对象 ssk_borrow_record
 *
 * <p>字段严格对应数据库表 ssk_borrow_record，不额外增加表以外的属性；
 * 列表展示所需的书籍信息（书名、作者、书架号、封面）与借阅状态由
 * {@link com.ruoyi.system.domain.vo.BorrowRecordVo} 承载。</p>
 *
 * <p>字段与表列的对应关系如下。表里还有一个 active_borrow_key，它是只服务于唯一约束的生成列
 * （未归还时算出值、已归还或已删除时为 NULL），不是业务字段，因此这里没有对应属性：</p>
 * <pre>
 *   id               -> id                 主键
 *   bookId           -> book_id            书籍ID，关联 ssk_book.id
 *   prisonerNumber   -> prisoner_number    囚号
 *   borrowTime       -> borrow_time        借出时间
 *   returnTime       -> return_time        应还时间
 *   actualReturnTime -> actual_return_time 实际归还时间，为空表示尚未归还
 *   createdBy        -> created_by         创建者用户ID（int，不是用户名）
 *   createdAt        -> created_at         创建时间
 *   updatedBy        -> updated_by         更新者用户ID
 *   updatedAt        -> updated_at         更新时间
 *   deleted          -> deleted            逻辑删除标记，false 未删除 / true 已删除
 * </pre>
 *
 * <p>本表不继承若依内置的 BaseEntity：审计字段名为 created_by / created_at / updated_by / updated_at，
 * 且 created_by 存的是用户ID（int），与 BaseEntity 的 create_by（varchar 用户名）不一致。</p>
 *
 * @author ruoyi
 */
public class BorrowRecord
{
    /** 主键ID */
    private Integer id;

    /** 书籍ID，关联 ssk_book.id */
    @NotNull(message = "请选择借出的书籍")
    private Integer bookId;

    /** 囚号 */
    @NotBlank(message = "囚号不能为空")
    @Size(max = 255, message = "囚号长度不能超过255个字符")
    private String prisonerNumber;

    /**
     * 借出时间
     *
     * <p>允许为空：为空时由 BorrowRecordBiz 套用默认值（当前时间）。
     * 前端借出弹窗始终会带上该值，这里的「可空」是为了让接口在不传时也能落到默认值上。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date borrowTime;

    /**
     * 应还时间
     *
     * <p>允许为空：为空时由 BorrowRecordBiz 套用默认值（借出时间 + 30 天）。</p>
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date returnTime;

    /** 实际归还时间，为空表示尚未归还 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date actualReturnTime;

    /** 创建者ID */
    private Integer createdBy;

    /** 创建时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 更新者ID */
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

    public Integer getBookId()
    {
        return bookId;
    }

    public void setBookId(Integer bookId)
    {
        this.bookId = bookId;
    }

    public String getPrisonerNumber()
    {
        return prisonerNumber;
    }

    public void setPrisonerNumber(String prisonerNumber)
    {
        this.prisonerNumber = prisonerNumber;
    }

    public Date getBorrowTime()
    {
        return borrowTime;
    }

    public void setBorrowTime(Date borrowTime)
    {
        this.borrowTime = borrowTime;
    }

    public Date getReturnTime()
    {
        return returnTime;
    }

    public void setReturnTime(Date returnTime)
    {
        this.returnTime = returnTime;
    }

    public Date getActualReturnTime()
    {
        return actualReturnTime;
    }

    public void setActualReturnTime(Date actualReturnTime)
    {
        this.actualReturnTime = actualReturnTime;
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
