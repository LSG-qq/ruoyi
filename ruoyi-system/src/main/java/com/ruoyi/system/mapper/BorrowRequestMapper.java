package com.ruoyi.system.mapper;

import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.system.domain.BorrowRequest;
import com.ruoyi.system.domain.vo.BorrowRequestVo;

/**
 * 借阅申请 数据层
 *
 * <p>方法命名遵循项目规范：findByXxx、updateByXxx，方法名不再重复携带模块名。</p>
 *
 * <p>管理后台只负责查询与改状态；申请的新增由终端接口触发，因此这一层有 create，
 * 但仍没有 delete（本模块不提供申请的删除）。</p>
 *
 * @author ruoyi
 */
public interface BorrowRequestMapper
{
    /**
     * 按条件分页查询借阅申请
     *
     * <p>书籍名称与状态都参与过滤，且刻意写在 SQL 里而不是查出来再过滤，
     * 否则 PageHelper 统计的总数与实际返回的行数会对不上。</p>
     *
     * @param bookName 书籍名称，模糊匹配 ssk_book.name，为空表示不过滤
     * @param status   申请状态（ready / resolved / rejected），为空表示不过滤
     * @return 借阅申请集合（含书籍信息）
     */
    public List<BorrowRequestVo> findPage(@Param("bookName") String bookName,
            @Param("status") String status);

    /**
     * 按囚号分页查询本人的借阅申请（终端的「我的申请」）
     *
     * <p>与 {@link #findPage} 的关键差别是囚号用**等值**匹配而不是模糊匹配：
     * 模糊匹配下囚号 20260001 会命中 20260011、20260001A 等一切包含它的串，
     * 终端场景里那等于把别人的申请暴露给本人，属于越权而非体验问题。
     * 后台的囚号筛选是「检索」语义、用户会输入片段，因此那边保持 like 不变。</p>
     *
     * <p>过滤条件写在 SQL 里而不是查出来再筛，否则 PageHelper 统计的总数与实际返回行数会对不上。</p>
     *
     * @param prisonerNumber 囚号（申请人标识），等值匹配，不允许为空
     * @param status         申请状态（ready / resolved / rejected），为空表示不过滤
     * @return 本人的借阅申请集合（含书籍信息）
     */
    public List<BorrowRequestVo> findPageByPrisoner(@Param("prisonerNumber") String prisonerNumber,
            @Param("status") String status);

    /**
     * 统计某囚号对某本书仍处于「待处理」的申请数
     *
     * <p>用于提交申请前的重复提交校验，是「同一囚号同书只能有一条待处理申请」的第一层拦截，
     * 只用于给出可读提示；真正的并发兜底是唯一索引 uk_ssk_borrow_request_active
     * （见 sql/ssk_borrow_request.sql 第 2.3 / 3.4 段）。</p>
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 待处理的申请数
     */
    public int countReadyByBookAndPrisoner(@Param("bookId") Integer bookId,
            @Param("prisonerNumber") String prisonerNumber);

    /**
     * 新增借阅申请
     *
     * <p>写入方只有终端：囚号、申请时间由业务层从登录令牌与系统时间取得，
     * 状态固定为「待处理」，逻辑删除标记固定写 0，均不接受调用方随意指定。
     * 生成列 active_request_key 不在这里赋值，由数据库按 deleted 与 status 自动算出；
     * 因此重复提交会撞上唯一索引抛出 DuplicateKeyException，由业务层转成可读提示。</p>
     *
     * @param request 借阅申请（bookId、prisonerNumber、borrowDays、status、createdAt 必须有值）
     * @return 结果
     */
    public int create(BorrowRequest request);

    /**
     * 根据主键查询借阅申请
     *
     * <p>审核前的校验与列表共用同一套查询列，返回结构完全一致。</p>
     *
     * @param id 主键
     * @return 借阅申请（含书籍信息），不存在或已逻辑删除时返回 null
     */
    public BorrowRequestVo findById(Integer id);

    /**
     * 更新申请状态（带前置状态条件）
     *
     * <p>{@code expectedStatus} 是这条 SQL 的关键：只有当前状态仍等于期望值时才会命中，
     * 影响行数 0 表示该申请已被别人处理过（或已被逻辑删除）。
     * 同意与拒绝共用这一条语句，靠传入的 {@code status} 区分结果，
     * 避免写两条只差一个常量的 update。</p>
     *
     * <p>它同时充当并发兜底：两个管理员同时点「同意」时，
     * 后到的那次会等前一个事务提交后重新判断状态，从而拿到影响行数 0 并被业务层拒绝。</p>
     *
     * @param id             申请ID
     * @param status         要写入的新状态
     * @param expectedStatus 期望的当前状态，通常为 ready
     * @param updatedBy      审核人ID
     * @param updatedAt      审核时间
     * @return 结果，0 表示申请不存在、已删除或状态已不是期望值
     */
    public int updateStatusById(@Param("id") Integer id, @Param("status") String status,
            @Param("expectedStatus") String expectedStatus, @Param("updatedBy") Integer updatedBy,
            @Param("updatedAt") Date updatedAt);
}
