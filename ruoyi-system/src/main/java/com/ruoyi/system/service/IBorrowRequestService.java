package com.ruoyi.system.service;

import java.util.Date;
import java.util.List;
import com.ruoyi.system.domain.BorrowRequest;
import com.ruoyi.system.domain.vo.BorrowRequestVo;

/**
 * 借阅申请 服务层
 *
 * <p>本层只做简单的增删查改，业务编排（提交申请的校验、同意时联动生成借阅记录、扣减库存等）
 * 统一放在 ruoyi-system 的 {@code com.ruoyi.system.biz.ssk.BorrowRequestBiz} 中
 * —— 终端（ruoyi-client）与管理后台（ruoyi-admin）共用同一份业务逻辑。</p>
 *
 * @author ruoyi
 */
public interface IBorrowRequestService
{
    /**
     * 按条件分页查询借阅申请
     *
     * @param bookName 书籍名称，为空表示不过滤
     * @param status   申请状态（ready / resolved / rejected），为空表示不过滤
     * @return 借阅申请集合
     */
    public List<BorrowRequestVo> findPage(String bookName, String status);

    /**
     * 按囚号分页查询本人的借阅申请（终端的「我的申请」）
     *
     * @param prisonerNumber 囚号，等值匹配，不允许为空
     * @param status         申请状态（ready / resolved / rejected），为空表示不过滤
     * @return 本人的借阅申请集合
     */
    public List<BorrowRequestVo> findPageByPrisoner(String prisonerNumber, String status);

    /**
     * 统计某囚号对某本书仍处于「待处理」的申请数
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 待处理的申请数
     */
    public int countReadyByBookAndPrisoner(Integer bookId, String prisonerNumber);

    /**
     * 新增借阅申请
     *
     * @param request 借阅申请
     * @return 结果
     */
    public int create(BorrowRequest request);

    /**
     * 根据主键查询借阅申请
     *
     * @param id 主键
     * @return 借阅申请，不存在或已逻辑删除时返回 null
     */
    public BorrowRequestVo findById(Integer id);

    /**
     * 更新申请状态（带前置状态条件）
     *
     * @param id             申请ID
     * @param status         要写入的新状态
     * @param expectedStatus 期望的当前状态，通常为 ready
     * @param updatedBy      审核人ID
     * @param updatedAt      审核时间
     * @return 结果，0 表示申请不存在、已删除或状态已不是期望值
     */
    public int updateStatusById(Integer id, String status, String expectedStatus,
            Integer updatedBy, Date updatedAt);
}
