package com.ruoyi.system.domain.vo;

import com.ruoyi.system.domain.BorrowRequest;

/**
 * 借阅申请展示对象
 *
 * <p>列表要在不点进详情的情况下就让管理员看清楚「是哪本书、放在哪个书架」，
 * 因此除申请本身的信息外，还需要展示所申请书籍的基本信息与书架号。
 * 按项目规范「实体类不增加表以外的字段」，这些关联查询出来的字段统一放在 VO 中。</p>
 *
 * <p>书架号 bookShelfCode 存的是字典 book_shelfs 的字典值，前端用
 * {@code <dict-tag>} 还原成标签；这里不返回字典标签，避免后端再去查一次字典表。</p>
 *
 * @author ruoyi
 */
public class BorrowRequestVo extends BorrowRequest
{
    /** 书籍名称（ssk_book.name） */
    private String bookName;

    /** 书籍作者（ssk_book.author） */
    private String bookAuthor;

    /** 书架号（ssk_book.shelf_code），前端按字典 book_shelfs 回显 */
    private String bookShelfCode;

    /** 书籍封面（ssk_book.cover） */
    private String bookCover;

    /**
     * 审核人姓名（sys_user.nick_name）
     *
     * <p>ssk_borrow_request.updated_by 存的是用户ID，列表要展示「谁批的」就得关联出姓名。
     * 关联时**不过滤 sys_user.del_flag**：用户被删之后，历史申请上的审核人姓名仍要能显示出来。</p>
     */
    private String updatedByName;

    public String getBookName()
    {
        return bookName;
    }

    public void setBookName(String bookName)
    {
        this.bookName = bookName;
    }

    public String getBookAuthor()
    {
        return bookAuthor;
    }

    public void setBookAuthor(String bookAuthor)
    {
        this.bookAuthor = bookAuthor;
    }

    public String getBookShelfCode()
    {
        return bookShelfCode;
    }

    public void setBookShelfCode(String bookShelfCode)
    {
        this.bookShelfCode = bookShelfCode;
    }

    public String getBookCover()
    {
        return bookCover;
    }

    public void setBookCover(String bookCover)
    {
        this.bookCover = bookCover;
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
