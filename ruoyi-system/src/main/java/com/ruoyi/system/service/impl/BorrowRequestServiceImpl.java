package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.BorrowRequest;
import com.ruoyi.system.domain.vo.BorrowRequestVo;
import com.ruoyi.system.mapper.BorrowRequestMapper;
import com.ruoyi.system.service.IBorrowRequestService;

/**
 * 借阅申请 服务层实现
 *
 * <p>只做参数透传，不含任何业务判断；事务边界由 {@code com.ruoyi.system.biz.ssk.BorrowRequestBiz} 控制。</p>
 *
 * @author ruoyi
 */
@Service
public class BorrowRequestServiceImpl implements IBorrowRequestService
{
    @Autowired
    private BorrowRequestMapper borrowRequestMapper;

    /**
     * 按条件分页查询借阅申请
     *
     * @param bookName 书籍名称
     * @param status   申请状态
     * @return 借阅申请集合
     */
    @Override
    public List<BorrowRequestVo> findPage(String bookName, String status)
    {
        return borrowRequestMapper.findPage(bookName, status);
    }

    /**
     * 按囚号分页查询本人的借阅申请
     *
     * @param prisonerNumber 囚号
     * @param status         申请状态
     * @return 借阅申请集合
     */
    @Override
    public List<BorrowRequestVo> findPageByPrisoner(String prisonerNumber, String status)
    {
        return borrowRequestMapper.findPageByPrisoner(prisonerNumber, status);
    }

    /**
     * 统计某囚号对某本书仍处于「待处理」的申请数
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 待处理的申请数
     */
    @Override
    public int countReadyByBookAndPrisoner(Integer bookId, String prisonerNumber)
    {
        return borrowRequestMapper.countReadyByBookAndPrisoner(bookId, prisonerNumber);
    }

    /**
     * 新增借阅申请
     *
     * @param request 借阅申请
     * @return 结果
     */
    @Override
    public int create(BorrowRequest request)
    {
        return borrowRequestMapper.create(request);
    }

    /**
     * 根据主键查询借阅申请
     *
     * @param id 主键
     * @return 借阅申请
     */
    @Override
    public BorrowRequestVo findById(Integer id)
    {
        return borrowRequestMapper.findById(id);
    }

    /**
     * 更新申请状态（带前置状态条件）
     *
     * @param id             申请ID
     * @param status         要写入的新状态
     * @param expectedStatus 期望的当前状态
     * @param updatedBy      审核人ID
     * @param updatedAt      审核时间
     * @return 结果
     */
    @Override
    public int updateStatusById(Integer id, String status, String expectedStatus,
            Integer updatedBy, Date updatedAt)
    {
        return borrowRequestMapper.updateStatusById(id, status, expectedStatus, updatedBy, updatedAt);
    }
}
