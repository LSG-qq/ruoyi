package com.ruoyi.system.domain;

import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * 借阅申请对象 ssk_borrow_request
 *
 * <p>记录囚犯在终端发起的借阅申请，管理员在后台逐条审核（同意 / 拒绝）。
 * 字段严格对应数据库表 ssk_borrow_request，不额外增加表以外的属性；
 * 列表展示所需的书籍信息（书名、作者、书架号、封面）由
 * {@link com.ruoyi.system.domain.vo.BorrowRequestVo} 承载。</p>
 *
 * <p>字段与表列的对应关系如下（10 个字段对 10 列，一个不多一个不少）：</p>
 * <pre>
 *   id             -> id                 主键
 *   bookId         -> book_id            书籍ID，关联 ssk_book.id
 *   prisonerNumber -> prisoner_number    囚号（申请人的标识），由终端写入，后台只读
 *   borrowDays     -> borrow_days        申请借阅天数，囚犯在终端选定，审核时可覆盖
 *   status         -> status             状态，取值见 {@link com.ruoyi.system.enums.BorrowRequestStatus}
 *   createdAt      -> created_at         申请时间（囚犯在终端提交的时刻）
 *   updatedBy      -> updated_by         审核人用户ID（int，不是用户名），未审核时为空
 *   updatedAt      -> updated_at         审核时间，未审核时为空
 *   deleted        -> deleted            逻辑删除标记，false 未删除 / true 已删除
 * </pre>
 *
 * <p>表中另有 active_request_key 一个 STORED 生成列，它只是「同一囚号同书只能有一条待处理申请」
 * 这条唯一约束的载体，取值由数据库按 deleted 与 status 自动算出，业务代码不读不写，
 * 因此与 ssk_borrow_record.active_borrow_key 一样不映射到实体上。</p>
 *
 * <p>本表不继承若依内置的 BaseEntity：审计字段名为 created_at / updated_by / updated_at，
 * 且 updated_by 存的是用户ID（int），与 BaseEntity 的 update_by（varchar 用户名）不一致。
 * 表里也没有 created_by —— 申请由囚犯在终端提交，不存在后台创建人，
 * 「谁申请的」由 prisoner_number 记，「谁审核的」由 updated_by 记。</p>
 *
 * <p>管理后台不提供申请的新增、修改与删除接口（后台只查看与改状态），
 * 申请的新增只由终端接口触发（{@code com.ruoyi.system.biz.ssk.BorrowRequestBiz#create}）。
 * 实体因此不挂任何 jakarta 校验注解，提交时的校验全部写在业务层。这同时规避了
 * 「新增与修改共用同一实体时校验注解对两个操作同时生效」的坑：没有注解，就不存在摘不干净的问题。</p>
 *
 * @author ruoyi
 */
public class BorrowRequest
{
    /** 主键ID */
    private Integer id;

    /** 书籍ID，关联 ssk_book.id */
    private Integer bookId;

    /**
     * 囚号（申请人的标识）
     *
     * <p>由终端在提交申请时写入，取值就是当前登录账号的用户名 —— 按「囚号即登录名」的约定，
     * 囚号本身就是一套 sys_user 账号，不另建映射表、不加额外字段。后台只读不改：
     * 申请是谁提的就该是谁借，不允许在审核环节改写成别人。</p>
     *
     * <p>列定义与 {@code ssk_borrow_record.prisoner_number} 完全一致（varchar(255) NOT NULL），
     * 「同意」时把本字段原样交给借阅记录，两边口径不会分叉。</p>
     */
    private String prisonerNumber;

    /**
     * 申请借阅天数
     *
     * <p>囚犯在终端提交申请时选定，含义是「我想要借多少天」。审核（同意）时若不指定天数，
     * 就取本字段作为借阅时长；管理员也可以在同意弹窗里改成别的天数覆盖它。
     * 因此它是「申请人的诉求」，不是最终借期 —— 最终借期落在
     * {@code ssk_borrow_record.borrow_time / return_time} 上，审核环节才产生。</p>
     *
     * <p>范围校验（1 ~ {@code BorrowRecordBiz.RETURN_TIME_MAX_DAYS} 天）属于只在提交时成立的约束，
     * 与库存默认值同理，写在业务层而不是实体上 —— 实体上的校验注解会对所有用到该实体的操作
     * 同时生效，标在这里会让只读场景也被牵连。</p>
     */
    private Integer borrowDays;

    /**
     * 申请状态
     *
     * <p>存的是 {@link com.ruoyi.system.enums.BorrowRequestStatus} 的 value
     * （ready / resolved / rejected），不是枚举的 name，也不是中文标签。</p>
     */
    private String status;

    /** 申请时间，由终端在提交时写入 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createdAt;

    /** 审核人ID，未审核时为空 */
    private Integer updatedBy;

    /** 审核时间，未审核时为空 */
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

    public Integer getBorrowDays()
    {
        return borrowDays;
    }

    public void setBorrowDays(Integer borrowDays)
    {
        this.borrowDays = borrowDays;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
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
