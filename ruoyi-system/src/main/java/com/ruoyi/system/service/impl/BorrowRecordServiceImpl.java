package com.ruoyi.system.service.impl;

import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.vo.BorrowRecordVo;
import com.ruoyi.system.mapper.BorrowRecordMapper;
import com.ruoyi.system.service.IBorrowRecordService;

/**
 * 借阅记录 服务层实现
 *
 * @author ruoyi
 */
@Service
public class BorrowRecordServiceImpl implements IBorrowRecordService
{
    @Autowired
    private BorrowRecordMapper borrowRecordMapper;

    /**
     * 按条件分页查询借阅记录
     *
     * @param record   查询条件（囚号）
     * @param bookName 书籍名称
     * @param status   借阅状态
     * @return 借阅记录集合
     */
    @Override
    public List<BorrowRecordVo> findPage(BorrowRecord record, String bookName, String status)
    {
        return borrowRecordMapper.findPage(record, bookName, status);
    }

    /**
     * 按囚号分页查询本人的借阅记录（等值匹配）
     *
     * @param prisonerNumber 囚号
     * @param status         借阅状态
     * @return 借阅记录集合
     */
    @Override
    public List<BorrowRecordVo> findPageByPrisoner(String prisonerNumber, String status)
    {
        return borrowRecordMapper.findPageByPrisoner(prisonerNumber, status);
    }

    /**
     * 根据主键查询借阅记录
     *
     * @param id 主键
     * @return 借阅记录
     */
    @Override
    public BorrowRecordVo findById(Integer id)
    {
        return borrowRecordMapper.findById(id);
    }

    /**
     * 统计某个囚号对某本书尚未归还的借阅记录数
     *
     * @param bookId         书籍ID
     * @param prisonerNumber 囚号
     * @return 未归还的借阅记录数
     */
    @Override
    public int countActiveByBookAndPrisoner(Integer bookId, String prisonerNumber)
    {
        return borrowRecordMapper.countActiveByBookAndPrisoner(bookId, prisonerNumber);
    }

    /**
     * 新增借阅记录
     *
     * @param record 借阅记录
     * @return 结果
     */
    @Override
    public int create(BorrowRecord record)
    {
        return borrowRecordMapper.create(record);
    }

    /**
     * 归还图书
     *
     * @param id               借阅记录ID
     * @param actualReturnTime 实际归还时间
     * @param updatedBy        操作人ID
     * @param updatedAt        操作时间
     * @return 结果
     */
    @Override
    public int updateReturnById(Integer id, Date actualReturnTime, Integer updatedBy, Date updatedAt)
    {
        return borrowRecordMapper.updateReturnById(id, actualReturnTime, updatedBy, updatedAt);
    }
}
