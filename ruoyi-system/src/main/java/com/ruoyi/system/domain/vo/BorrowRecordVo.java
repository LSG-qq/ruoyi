package com.ruoyi.system.domain.vo;

import com.ruoyi.system.domain.BorrowRecord;

/**
 * 借阅记录展示对象
 *
 * <p>列表除借阅记录本身的信息外，还需要展示所借书籍的基本信息与书架号，
 * 按项目规范「实体类不增加表以外的字段」，这些关联查询出来的字段统一放在 VO 中。</p>
 *
 * <p>借阅状态也由 SQL 统一计算后返回，避免前端各自判断导致口径不一致，
 * 同时让「按状态筛选」能够在数据库层完成，不破坏 PageHelper 分页。</p>
 *
 * @author ruoyi
 */
public class BorrowRecordVo extends BorrowRecord
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
     * 借阅状态，取值：
     * <ul>
     *     <li>returned —— 已归还（actual_return_time 不为空）</li>
     *     <li>overdue —— 已逾期（未归还且应还时间已过）</li>
     *     <li>borrowing —— 借出中（未归还且未到期）</li>
     * </ul>
     */
    private String status;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }
}
