package com.ruoyi.system.biz.ssk;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.system.domain.BorrowRecord;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.domain.vo.BorrowRecordVo;
import com.ruoyi.system.service.IBookService;
import com.ruoyi.system.service.IBorrowRecordService;

/**
 * 借阅记录 业务层
 *
 * <p>承接 controller 与 service 之间的业务编排，包含以下规则：</p>
 * <ul>
 *     <li>借出：借出时间默认当前时间，应还时间默认借出时间 + {@link #DEFAULT_RETURN_DAYS} 天，两者均允许在合理范围内调整；</li>
 *     <li>借出：同一囚号对同一本书存在未归还记录时不允许重复借出 —— 先查一次给出可读提示，
 *         并以数据库唯一索引 uk_ssk_borrow_record_active 兜底，防并发请求同时穿透先查校验；</li>
 *     <li>借出：库存扣减交给 SQL 的「不得扣成负数」条件完成，扣减失败即提示库存不足；</li>
 *     <li>还书：实际归还时间固定取当前系统时间，不接受前端传入；</li>
 *     <li>还书：SQL 层带「未归还」条件，重复提交只会成功一次，库存也只回补一次；</li>
 *     <li>审计字段（创建人、创建时间、更新人、更新时间）全部由后端生成。</li>
 * </ul>
 *
 * @author ruoyi
 */
@Component
public class BorrowRecordBiz
{
    /** 囚号最大长度，与 ssk_borrow_record.prisoner_number 字段长度保持一致 */
    private static final int PRISONER_NUMBER_MAX_LENGTH = 255;

    /** 应还时间的默认借期天数：借出时间 + 30 天 */
    private static final int DEFAULT_RETURN_DAYS = 30;

    /** 借出时间允许往前补录的最大天数，避免误填成远古日期 */
    private static final int BORROW_TIME_MAX_BACK_DAYS = 90;

    /**
     * 借期上限：应还时间不得晚于借出时间 + 365 天
     *
     * <p>公开给同模块的 {@code BorrowRequestBiz} 复用：借阅申请同意时也要限制借阅天数，
     * 上限若在两处各写一份，改一处漏一处就会出现「申请这边能批 400 天、借出接口却拒绝」。
     * 这里是唯一的定义点。</p>
     */
    public static final int RETURN_TIME_MAX_DAYS = 365;

    /** 借出时间允许晚于当前时间的分钟数，仅用于吸收前端与服务器的时钟偏差 */
    private static final int CLOCK_TOLERANCE_MINUTES = 5;

    /** 借阅状态：借出中（未归还且未到期） */
    private static final String STATUS_BORROWING = "borrowing";

    /** 借阅状态：已归还 */
    private static final String STATUS_RETURNED = "returned";

    /** 借阅状态：已逾期（未归还且应还时间已过） */
    private static final String STATUS_OVERDUE = "overdue";

    /**
     * 借阅状态的中文标签
     *
     * <p>与上面的取值常量成对定义：前端下拉与列表标签都用这一份，
     * 不另外维护状态字典，避免「改了取值、前端还写着旧标签」。
     * 注意这些标签本身以「已」开头，拼接提示语时不要写成「已 + 标签」。</p>
     */
    private static final String STATUS_BORROWING_LABEL = "借出中";

    private static final String STATUS_OVERDUE_LABEL = "已逾期";

    private static final String STATUS_RETURNED_LABEL = "已归还";

    /** 允许的借阅状态集合，非集合内的值按「不筛选」处理 */
    private static final List<String> VALID_STATUSES =
            Arrays.asList(STATUS_BORROWING, STATUS_RETURNED, STATUS_OVERDUE);

    /** 借出时的库存增量 */
    private static final int STOCK_BORROW_DELTA = -1;

    /** 归还时的库存增量 */
    private static final int STOCK_RETURN_DELTA = 1;

    @Autowired
    private IBorrowRecordService borrowRecordService;

    @Autowired
    private IBookService bookService;

    /**
     * 按条件分页查询借阅记录
     *
     * @param record   查询条件（囚号），为空表示不带条件
     * @param bookName 书籍名称，为空表示不带条件
     * @param status   借阅状态，为空或非法值表示不带条件
     * @return 借阅记录集合
     */
    public List<BorrowRecordVo> findPage(BorrowRecord record, String bookName, String status)
    {
        BorrowRecord query = new BorrowRecord();
        if (record != null)
        {
            // 查询条件统一去空格，空串按未填写处理
            query.setPrisonerNumber(normalizeQueryText(record.getPrisonerNumber()));
        }
        return borrowRecordService.findPage(query, normalizeQueryText(bookName), normalizeStatus(status));
    }

    /**
     * 查询本人的借阅记录（终端的「我的借阅」）
     *
     * <p>囚号取自登录令牌，本方法不收囚号参数：一旦接受囚号入参，
     * 终端只要改一个参数就能翻看别人的借阅记录。查询走**等值**匹配，
     * 不复用后台的模糊匹配（否则囚号 20260001 会命中 20260011）。</p>
     *
     * @param status 借阅状态，为空或非法值表示不筛选
     * @return 本人的借阅记录集合
     */
    public List<BorrowRecordVo> findMyPage(String status)
    {
        return borrowRecordService.findPageByPrisoner(currentPrisonerNumber(), normalizeStatus(status));
    }

    /**
     * 查询借阅状态的下拉选项
     *
     * <p>取值与标签都来自本类的常量，与 SQL 里计算状态用的是同一组取值。
     * 终端不在自己的前端里抄一份状态字典：这里改了取值或标签，下拉与列表标签会跟着变。
     * 返回结构固定为 {@code [{value, label}]}；「全部」这一项属于展示层概念（值为空），
     * 由前端自行补上，不放在这里。</p>
     *
     * @return 状态下拉选项
     */
    public List<Map<String, String>> findStatusOptions()
    {
        List<Map<String, String>> options = new ArrayList<>();
        options.add(statusOption(STATUS_BORROWING, STATUS_BORROWING_LABEL));
        options.add(statusOption(STATUS_OVERDUE, STATUS_OVERDUE_LABEL));
        options.add(statusOption(STATUS_RETURNED, STATUS_RETURNED_LABEL));
        return options;
    }

    /**
     * 组装一个状态选项
     *
     * @param value 状态取值
     * @param label 中文标签
     * @return 选项
     */
    private Map<String, String> statusOption(String value, String label)
    {
        Map<String, String> option = new LinkedHashMap<>();
        option.put("value", value);
        option.put("label", label);
        return option;
    }

    /**
     * 获取当前登录账号的用户名，即囚号
     *
     * <p>「囚号即登录名」是本模块的既定约定，不需要再查映射表。
     * 返回前统一去首尾空格，避免因为一个尾随空格导致同一囚号在库里出现两种写法、
     * 等值查询查不全。</p>
     *
     * @return 囚号
     */
    private String currentPrisonerNumber()
    {
        String username = StringUtils.trim(SecurityUtils.getUsername());
        if (StringUtils.isEmpty(username))
        {
            throw new ServiceException("获取当前登录用户信息失败");
        }
        return username;
    }

    /**
     * 查询借出弹窗的书籍下拉数据
     *
     * <p>返回全部未删除的书籍（含库存），由前端把库存为 0 的选项置灰，
     * 让使用者能看出「这本书存在但已被借完」，而不是在下拉里凭空消失。</p>
     *
     * @return 书籍集合
     */
    public List<BookVo> findBookOptions()
    {
        // 走下拉专用查询：不分页（要能选到任意一本书），且不返回简介、图片集等借出弹窗用不到的大字段
        return bookService.findOptionList();
    }

    /**
     * 借出图书：新增借阅记录并扣减库存
     *
     * @param record 借阅记录，只需 bookId、prisonerNumber，时间字段可为空
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(BorrowRecord record)
    {
        if (record == null)
        {
            throw new ServiceException("借阅信息不能为空");
        }
        BookVo book = requireBook(record.getBookId());
        record.setPrisonerNumber(requirePrisonerNumber(record.getPrisonerNumber()));
        normalizeTimes(record);

        // 第一层：先查一次，命中时能给出「谁借了哪本书未还」这种可读提示
        if (borrowRecordService.countActiveByBookAndPrisoner(record.getBookId(), record.getPrisonerNumber()) > 0)
        {
            throw new ServiceException(duplicateBorrowMessage(record.getPrisonerNumber(), book.getName()));
        }

        Integer operator = currentUserId();
        Date now = new Date();
        // 先扣库存：扣减失败说明库存不足，此时借阅记录尚未写入，无需额外回滚处理
        if (bookService.updateStockById(book.getId(), STOCK_BORROW_DELTA, operator, now) == 0)
        {
            throw new ServiceException("《" + book.getName() + "》当前无可借库存，无法借出");
        }

        // 主键与审计字段一律由后端生成，实际归还时间固定为空
        record.setId(null);
        record.setActualReturnTime(null);
        record.setCreatedBy(operator);
        record.setCreatedAt(now);
        record.setUpdatedBy(null);
        record.setUpdatedAt(null);
        record.setDeleted(null);
        try
        {
            return borrowRecordService.create(record);
        }
        catch (DuplicateKeyException e)
        {
            // 第二层：唯一索引 uk_ssk_borrow_record_active 的兜底。
            // 两个并发请求可能都通过了上面的先查校验，此时后写入的那条会被数据库拒绝，
            // 这里转成与先查命中一致的提示，避免把数据库异常直接抛给使用者。
            // 事务会整体回滚，前面扣掉的库存不会留下。
            throw new ServiceException(duplicateBorrowMessage(record.getPrisonerNumber(), book.getName()));
        }
    }

    /**
     * 重复借阅的提示文案，先查命中与唯一约束拦截两处共用同一句
     *
     * @param prisonerNumber 囚号
     * @param bookName       书籍名称
     * @return 提示文案
     */
    private String duplicateBorrowMessage(String prisonerNumber, String bookName)
    {
        return "囚号「" + prisonerNumber + "」借阅的《" + bookName + "》尚未归还，请先归还后再借出";
    }

    /**
     * 归还图书：写入实际归还时间并回补库存
     *
     * <p>归还时间是「当下发生的事实」，因此固定取当前系统时间，不接受前端传入；
     * 重复提交时 SQL 的影响行数为 0，库存也只会回补一次。</p>
     *
     * @param id 借阅记录ID
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int returnBook(Integer id)
    {
        BorrowRecordVo record = requireRecord(id);
        if (record.getActualReturnTime() != null)
        {
            throw new ServiceException("囚号「" + record.getPrisonerNumber() + "」借阅的《" + record.getBookName()
                    + "》已于" + formatDateTime(record.getActualReturnTime()) + "归还，无需重复操作");
        }

        Integer operator = currentUserId();
        Date now = new Date();
        if (borrowRecordService.updateReturnById(id, now, operator, now) == 0)
        {
            throw new ServiceException("还书失败，该借阅记录可能已被归还或已不存在，请刷新后重试");
        }
        // 回补库存：书籍被逻辑删除时也会回补，避免归还动作把库存丢掉
        if (bookService.updateStockById(record.getBookId(), STOCK_RETURN_DELTA, operator, now) == 0)
        {
            throw new ServiceException("还书失败：对应的书籍已不存在，请联系管理员核对借阅数据");
        }
        return 1;
    }

    /**
     * 处理借出时间与应还时间：为空时套用默认值，非空时校验合理范围
     *
     * <p>借出时间默认当前时间，允许往前补录最多 {@link #BORROW_TIME_MAX_BACK_DAYS} 天、不可晚于当前时间；
     * 应还时间默认借出时间 + {@link #DEFAULT_RETURN_DAYS} 天，必须晚于借出时间且不超过
     * 借出时间 + {@link #RETURN_TIME_MAX_DAYS} 天。</p>
     *
     * @param record 借阅记录，方法内直接修改该对象
     */
    private void normalizeTimes(BorrowRecord record)
    {
        Date now = new Date();
        Date borrowTime = record.getBorrowTime() == null ? now : record.getBorrowTime();
        if (borrowTime.after(offsetMinutes(now, CLOCK_TOLERANCE_MINUTES)))
        {
            throw new ServiceException("借出时间不能晚于当前时间");
        }
        if (borrowTime.before(offsetDays(now, -BORROW_TIME_MAX_BACK_DAYS)))
        {
            throw new ServiceException("借出时间不能早于" + BORROW_TIME_MAX_BACK_DAYS + "天前");
        }
        record.setBorrowTime(borrowTime);

        Date returnTime = record.getReturnTime() == null
                ? offsetDays(borrowTime, DEFAULT_RETURN_DAYS) : record.getReturnTime();
        if (!returnTime.after(borrowTime))
        {
            throw new ServiceException("应还时间必须晚于借出时间");
        }
        if (returnTime.after(offsetDays(borrowTime, RETURN_TIME_MAX_DAYS)))
        {
            throw new ServiceException("应还时间不能超过借出时间后的" + RETURN_TIME_MAX_DAYS + "天");
        }
        record.setReturnTime(returnTime);
    }

    /**
     * 校验并返回囚号
     *
     * <p>囚号只做去空格与长度校验，不限制字符类型，兼容纯数字与带字母前缀等不同编码规则。</p>
     *
     * @param prisonerNumber 囚号
     * @return 去除首尾空格后的囚号
     */
    private String requirePrisonerNumber(String prisonerNumber)
    {
        String trimmed = StringUtils.trim(prisonerNumber);
        if (StringUtils.isEmpty(trimmed))
        {
            throw new ServiceException("囚号不能为空");
        }
        if (trimmed.length() > PRISONER_NUMBER_MAX_LENGTH)
        {
            throw new ServiceException("囚号长度不能超过" + PRISONER_NUMBER_MAX_LENGTH + "个字符");
        }
        return trimmed;
    }

    /**
     * 查询书籍，不存在时抛出业务异常
     *
     * @param bookId 书籍ID
     * @return 书籍信息
     */
    private BookVo requireBook(Integer bookId)
    {
        if (bookId == null)
        {
            throw new ServiceException("请选择借出的书籍");
        }
        BookVo book = bookService.findById(bookId);
        if (book == null)
        {
            throw new ServiceException("所选书籍不存在或已被删除，请重新选择");
        }
        return book;
    }

    /**
     * 查询借阅记录，不存在时抛出业务异常
     *
     * @param id 借阅记录ID
     * @return 借阅记录
     */
    private BorrowRecordVo requireRecord(Integer id)
    {
        if (id == null)
        {
            throw new ServiceException("借阅记录ID不能为空");
        }
        BorrowRecordVo record = borrowRecordService.findById(id);
        if (record == null)
        {
            throw new ServiceException("借阅记录不存在或已被删除");
        }
        return record;
    }

    /**
     * 获取当前登录用户ID，用于填充创建人/更新人
     *
     * @return 当前登录用户ID
     */
    private Integer currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("获取当前登录用户信息失败");
        }
        return userId.intValue();
    }

    /**
     * 查询条件去空格，空串按未填写处理
     *
     * @param value 条件值
     * @return 处理后的条件值，未填写时返回 null
     */
    private String normalizeQueryText(String value)
    {
        String trimmed = StringUtils.trim(value);
        return StringUtils.isEmpty(trimmed) ? null : trimmed;
    }

    /**
     * 借阅状态归一化，非法值按「不筛选」处理
     *
     * @param status 前端传入的状态
     * @return 合法状态，非法或为空时返回 null
     */
    private String normalizeStatus(String status)
    {
        String trimmed = StringUtils.trim(status);
        return VALID_STATUSES.contains(trimmed) ? trimmed : null;
    }

    /**
     * 在指定时间上偏移天数
     *
     * @param base 基准时间
     * @param days 偏移天数，负数为往前
     * @return 偏移后的时间
     */
    private Date offsetDays(Date base, int days)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.DAY_OF_MONTH, days);
        return calendar.getTime();
    }

    /**
     * 在指定时间上偏移分钟数
     *
     * @param base    基准时间
     * @param minutes 偏移分钟数
     * @return 偏移后的时间
     */
    private Date offsetMinutes(Date base, int minutes)
    {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(base);
        calendar.add(Calendar.MINUTE, minutes);
        return calendar.getTime();
    }

    /**
     * 把时间格式化为「yyyy-MM-dd HH:mm:ss」，用于拼接提示信息
     *
     * @param time 时间
     * @return 格式化结果
     */
    private String formatDateTime(Date time)
    {
        if (time == null)
        {
            return "";
        }
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time);
    }
}
