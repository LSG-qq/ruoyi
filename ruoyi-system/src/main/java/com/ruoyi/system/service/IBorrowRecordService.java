package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.vo.BorrowRecordVo;

/**
 * 借阅记录 服务层
 *
 * <p>本层只做简单的增删查改，业务编排统一放在 ruoyi-system 的
 * {@code com.ruoyi.system.biz.ssk.BorrowRecordBiz} 中
 * —— 终端（ruoyi-client）与管理后台（ruoyi-admin）共用同一份业务逻辑。</p>
 *
 * @author ruoyi
 */
public interface IBorrowRecordService
{
    /**
     * 按条件分页查询借阅记录
     *
     * @param record   查询条件（囚号）
     * @param bookName 书籍名称，为空表示不过滤
     * @param status   借阅状态（borrowing / returned / overdue），为空表示不过滤
     * @return 借阅记录集合
     */
    public List<BorrowRecordVo> findPage(BorrowRecord record, String bookName, String status);

    /**
     * 按囚号分页查询本人的借阅记录（终端的「我的借阅」）
     *
     * <p>囚号用等值匹配，不复用 {@link #findPage} 的模糊匹配 —— 后者会把别人的记录
     * 一起查出来（囚号 20260001 命中 20260011）。</p>
     *
     * @param prisonerNumber 囚号，等值匹配，不允许为空
     * @param status         借阅状态（borrowing / returned / overdue），为空表示不过滤
     * @return 本人的借阅记录集合
     */
    public List<BorrowRecordVo> findPageByPrisoner(String prisonerNumber, String status);

    /**
     * 根据主键查询借阅记录
     *
     * @param id 主键
     * @return 借阅记录
     */
    public BorrowRecordVo findById(Integer id);

    /**
     * 统计某个囚号对某本书尚未归还的借阅记录数
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 未归还的借阅记录数
     */
    public int countActiveByBookAndPrisoner(Integer bookId, String prisonerNumber);

    /**
     * 新增借阅记录
     *
     * @param record 借阅记录
     * @return 结果
     */
    public int create(BorrowRecord record);

    /**
     * 归还图书
     *
     * @param id               借阅记录ID
     * @param actualReturnTime 实际归还时间
     * @param updatedBy        操作人ID
     * @param updatedAt        操作时间
     * @return 结果，0 表示该记录不存在、已被删除或已经归还
     */
    public int updateReturnById(Integer id, Date actualReturnTime, Integer updatedBy, Date updatedAt);
}
