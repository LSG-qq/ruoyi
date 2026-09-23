package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.vo.BorrowRecordVo;

/**
 * 借阅记录 数据层
 *
 * <p>方法命名遵循项目规范：create、findByXxx、updateByXxx、countByXxx，方法名不再重复携带模块名。</p>
 *
 * <p>本表带有一个生成列 active_borrow_key 与唯一索引 uk_ssk_borrow_record_active：
 * 只有「未删除且未归还」的记录会算出值，已归还/已删除时算出 NULL，
 * 因此该约束等价于「同一囚号对同一本书只能有一条未归还记录」，并在还书后自动释放。
 * 新增时无需为它赋值，由数据库生成；违反约束会抛出 DuplicateKeyException，由业务层转成可读提示。</p>
 *
 * @author ruoyi
 */
public interface BorrowRecordMapper
{
    /**
     * 按条件分页查询借阅记录
     *
     * <p>书籍名称与借阅状态都不属于 ssk_borrow_record 表字段，
     * 无法绑定到实体上，因此单独作为参数传入。</p>
     *
     * @param record   查询条件（囚号），未填写的条件不参与过滤
     * @param bookName 书籍名称，模糊匹配 ssk_book.name，为空表示不过滤
     * @param status   借阅状态（borrowing / returned / overdue），为空表示不过滤
     * @return 借阅记录集合（含书籍信息与借阅状态）
     */
    public List<BorrowRecordVo> findPage(@Param("record") BorrowRecord record,
            @Param("bookName") String bookName, @Param("status") String status);

    /**
     * 按囚号分页查询本人的借阅记录（终端的「我的借阅」）
     *
     * <p>与 {@link #findPage} 的关键差别是囚号用**等值**匹配而不是模糊匹配：
     * findPage 里的 like '%囚号%' 是给后台做检索用的，囚号 20260001 会命中 20260011
     * 等一切包含它的串；终端直接复用它，等于把别人的借阅记录暴露给本人。
     * 因此这里单独出一条等值语句，两边刻意不共用。</p>
     *
     * <p>借阅状态与后台列表口径完全一致（已归还 / 已逾期 / 借出中），
     * 由 SQL 统一计算后返回，避免两个入口各自判断导致同一条记录显示不同的状态。
     * 状态筛选同样写在 SQL 里，保证 PageHelper 统计的总数与实际返回行数一致。</p>
     *
     * @param prisonerNumber 囚号，等值匹配，不允许为空
     * @param status         借阅状态（borrowing / returned / overdue），为空表示不过滤
     * @return 本人的借阅记录集合（含书籍信息与借阅状态）
     */
    public List<BorrowRecordVo> findPageByPrisoner(@Param("prisonerNumber") String prisonerNumber,
            @Param("status") String status);

    /**
     * 根据主键查询借阅记录
     *
     * @param id 主键
     * @return 借阅记录（含书籍信息与借阅状态）
     */
    public BorrowRecordVo findById(Integer id);

    /**
     * 统计某个囚号对某本书尚未归还的借阅记录数
     *
     * <p>用于借出前的重复借阅校验：同一囚号对同一本书存在未归还记录时不允许再次借出。</p>
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 未归还的借阅记录数
     */
    public int countActiveByBookAndPrisoner(@Param("bookId") Integer bookId,
            @Param("prisonerNumber") String prisonerNumber);

    /**
     * 新增借阅记录
     *
     * <p>实际归还时间固定写入 null，审计字段与逻辑删除标记由业务层填好后带入。
     * 若同一囚号对同一本书已存在未归还记录，数据库的唯一索引 uk_ssk_borrow_record_active
     * 会拒绝写入并抛出 DuplicateKeyException。</p>
     *
     * @param record 借阅记录
     * @return 结果
     */
    public int create(BorrowRecord record);

    /**
     * 归还图书：写入实际归还时间
     *
     * <p>SQL 中带 {@code actual_return_time is null} 条件，保证并发或重复提交时只有第一次能成功，
     * 返回 0 表示该记录不存在、已被删除或已经归还。
     * 归还后生成列 active_borrow_key 变为 NULL，唯一约束随之释放，同囚号可以再次借出该书。</p>
     *
     * @param id               借阅记录ID
     * @param actualReturnTime 实际归还时间
     * @param updatedBy        操作人ID
     * @param updatedAt        操作时间
     * @return 结果
     */
    public int updateReturnById(@Param("id") Integer id, @Param("actualReturnTime") Date actualReturnTime,
            @Param("updatedBy") Integer updatedBy, @Param("updatedAt") Date updatedAt);
}
