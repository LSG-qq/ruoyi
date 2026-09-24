package com.ruoyi.system.biz.ssk;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.domain.vo.BookVo;
import com.ruoyi.system.domain.vo.ClientBookVo;
import com.ruoyi.system.service.IBookCategoryService;
import com.ruoyi.system.service.IBookService;

/**
 * 图书 业务层
 *
 * <p>承接 controller 与 service 之间的业务编排，包含以下规则：</p>
 * <ul>
 *     <li>查询条件为空时不参与过滤，类目筛选为「选中类目 + 其全部下级类目」；</li>
 *     <li>库存是借阅运营量：新增时接受初始库存，修改时忽略（库存只由借出/归还按增量变动）；</li>
 *     <li>创建人、创建时间、更新人、更新时间、逻辑删除标记全部由后端生成，不接受前端传入。</li>
 * </ul>
 *
 * <p>字段层面的校验与规范化（长度上限、类目ID格式、图片集张数、库存默认值与下限）
 * 集中在 {@link BookFieldRules}，本类只负责编排：取数据、定顺序、补审计字段。
 * 这样分开的好处是字段规则可以脱离 Spring 上下文直接测，本类也不会因为
 * 规则越加越多而突破工程规范的类行数上限。</p>
 *
 * @author ruoyi
 */
@Component
public class BookBiz
{
    @Autowired
    private IBookService bookService;

    @Autowired
    private IBookCategoryService bookCategoryService;

    /**
     * 按条件分页查询图书
     *
     * @param book       查询条件（书籍名称、作者、书架号），为空表示不带条件
     * @param categoryId 左侧类目树选中的类目ID，为空或 0 表示「全部」
     * @return 图书集合
     */
    public List<BookVo> findPage(Book book, Integer categoryId)
    {
        Book query = new Book();
        if (book != null)
        {
            // 查询条件统一去空格，空串按未填写处理
            query.setName(BookFieldRules.normalizeQueryText(book.getName()));
            query.setAuthor(BookFieldRules.normalizeQueryText(book.getAuthor()));
            query.setShelfCode(BookFieldRules.normalizeQueryText(book.getShelfCode()));
        }
        return bookService.findPage(query, resolveCategoryIds(categoryId));
    }

    /**
     * 根据主键查询图书，不存在时抛出业务异常
     *
     * @param id 主键
     * @return 图书信息
     */
    public BookVo findById(Integer id)
    {
        return requireBook(id);
    }

    /**
     * 按条件分页查询图书（终端浏览用）
     *
     * <p>与后台列表共用同一套查询条件、类目展开与分页（直接复用 {@link #findPage}），
     * 差别只在**返回字段**：审计字段（created_by / created_at / updated_by / updated_at / deleted）
     * 属于内部运营信息，终端不需要也不应该拿到，因此投影成 {@link ClientBookVo}。
     * 不直接返回 BookVo 的目的正是避免这些字段出现在终端响应体里。</p>
     *
     * <p>投影安排在分页之后：PageHelper 的分页参数在调用链的第一次查询就被消费掉了，
     * 这里只是在已分页的结果上做字段裁剪，不会影响总数与每页条数。</p>
     *
     * @param book       查询条件（书籍名称、作者、书架号），为空表示不带条件
     * @param categoryId 选中的类目ID，为空或 0 表示「全部」
     * @return 图书集合（仅终端可见字段）
     */
    public List<ClientBookVo> findClientPage(Book book, Integer categoryId)
    {
        List<ClientBookVo> result = new ArrayList<>();
        for (BookVo source : findPage(book, categoryId))
        {
            result.add(toClientBook(source));
        }
        return result;
    }

    /**
     * 根据主键查询图书详情（终端浏览用）
     *
     * <p>与列表共用同一条查询（{@link #findById}）与同一套字段投影，
     * 因此详情里看到的书名、作者、简介、图片集与列表里那一本必然一致 ——
     * 若在详情侧重写一条 SQL，将来加了字段很容易只补一处。</p>
     *
     * <p>图书不存在或已被逻辑删除时，{@code requireBook} 抛出可读的业务异常，
     * 由全局异常处理器转成提示，终端不会拿到一个空壳对象去渲染空白详情。</p>
     *
     * @param id 图书ID
     * @return 图书详情（仅终端可见字段）
     */
    public ClientBookVo findClientById(Integer id)
    {
        return toClientBook(findById(id));
    }

    /**
     * 把图书展示对象裁剪为终端可见字段
     *
     * @param source 含审计字段的图书展示对象
     * @return 仅含终端可见字段的图书对象
     */
    private ClientBookVo toClientBook(BookVo source)
    {
        ClientBookVo target = new ClientBookVo();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setDescription(source.getDescription());
        target.setAuthor(source.getAuthor());
        target.setCategoryIds(source.getCategoryIds());
        target.setShelfCode(source.getShelfCode());
        target.setCover(source.getCover());
        target.setImages(source.getImages());
        target.setStockQuantity(source.getStockQuantity());
        return target;
    }

    /**
     * 新增图书
     *
     * @param book 图书信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int create(Book book)
    {
        if (book == null)
        {
            throw new ServiceException("图书信息不能为空");
        }
        applyEditableFields(book, bookCategoryService.findList());
        // 库存只在新增时有意义：为空按默认库存处理，负数在这里被拒绝。
        // 校验放在调用方而不是 applyEditableFields 里，是为了让"修改图书"完全不碰库存
        // —— 被忽略的字段不应该还能让整单失败。
        book.setStockQuantity(BookFieldRules.normalizeStockQuantity(book.getStockQuantity()));
        // 主键与审计字段一律由后端生成
        book.setId(null);
        book.setCreatedBy(currentUserId());
        book.setCreatedAt(new Date());
        book.setUpdatedBy(null);
        book.setUpdatedAt(null);
        book.setDeleted(null);
        return bookService.create(book);
    }

    /**
     * 修改图书
     *
     * <p>可修改的是书籍属性，不含库存：库存是借阅运营量，只由借出/归还经库存增量语句变动。
     * 若编辑时把表单上的库存一起落库，用户在弹窗里停留期间发生的借出/归还就会被这次编辑覆盖掉。</p>
     *
     * @param book 图书信息
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int updateById(Book book)
    {
        if (book == null)
        {
            throw new ServiceException("图书信息不能为空");
        }
        requireBook(book.getId());
        applyEditableFields(book, bookCategoryService.findList());
        // 库存不参与修改：置空后数据层不会更新 stock_quantity 列。
        // 即便调用方直接传了库存（绕过前端），也在这里被丢掉，由后端兜底而不是指望前端不传。
        book.setStockQuantity(null);
        // 创建信息与逻辑删除标记不允许被前端覆盖
        book.setCreatedBy(null);
        book.setCreatedAt(null);
        book.setDeleted(null);
        book.setUpdatedBy(currentUserId());
        book.setUpdatedAt(new Date());
        return bookService.updateById(book);
    }

    /**
     * 批量删除图书（逻辑删除）
     *
     * <p>删除是幂等的：已被删除的图书再次提交不会报错，也不会影响其它数据。</p>
     *
     * @param ids 需要删除的图书ID数组
     * @return 结果
     */
    @Transactional(rollbackFor = Exception.class)
    public int deleteByIds(Integer[] ids)
    {
        if (ids == null || ids.length == 0)
        {
            throw new ServiceException("请选择要删除的图书");
        }
        Set<Integer> targetIds = new LinkedHashSet<>();
        for (Integer id : ids)
        {
            if (id != null)
            {
                targetIds.add(id);
            }
        }
        if (targetIds.isEmpty())
        {
            throw new ServiceException("请选择要删除的图书");
        }
        return bookService.deleteByIds(targetIds.toArray(new Integer[0]), currentUserId(), new Date());
    }

    /**
     * 处理可编辑字段：校验、去空格、格式化类目与图片集，并派生封面
     *
     * <p>刻意不含库存：库存在新增与修改下的归属不同（新增落初始库存、修改完全忽略），
     * 由各自的调用方显式处理，见 {@link #create} 与 {@link #updateById}。</p>
     *
     * <p>具体规则在 {@link BookFieldRules}，这里只按顺序把结果写回实体。</p>
     *
     * @param book       图书信息，方法内直接修改该对象
     * @param categories 全部未删除的类目，用于校验类目ID是否合法
     */
    private void applyEditableFields(Book book, List<BookCategory> categories)
    {
        book.setName(BookFieldRules.requireText(book.getName(), BookFieldRules.NAME_MAX_LENGTH, "书籍名称"));
        book.setDescription(BookFieldRules.requireText(book.getDescription(),
                BookFieldRules.DESCRIPTION_MAX_LENGTH, "书籍描述"));
        book.setAuthor(BookFieldRules.requireText(book.getAuthor(), BookFieldRules.AUTHOR_MAX_LENGTH, "作者"));
        book.setShelfCode(BookFieldRules.requireText(book.getShelfCode(),
                BookFieldRules.SHELF_CODE_MAX_LENGTH, "书架号"));
        book.setCategoryIds(BookFieldRules.normalizeCategoryIds(book.getCategoryIds(), categories));
        String images = BookFieldRules.normalizeImages(book.getImages());
        book.setImages(images);
        // 封面固定为图片集的第一张图，空图片集时封面置空
        book.setCover(BookFieldRules.firstImage(images));
    }

    /**
     * 解析左侧类目树选中的类目ID，展开为「自身 + 全部下级」的ID集合
     *
     * @param categoryId 选中的类目ID，为空或 0 表示「全部」
     * @return 类目ID集合，返回 null 表示不按类目过滤
     */
    private List<Integer> resolveCategoryIds(Integer categoryId)
    {
        if (categoryId == null || categoryId.intValue() <= 0)
        {
            return null;
        }
        // 取「全部类目」是一次真实的 SQL，而 PageHelper 的 startPage() 只对**下一条** SQL 生效。
        // 控制器是 startPage() → findPage()，本方法又在 findPage() 内部先被调用，
        // 若不作处理，被分页的就会是这次类目查询而不是图书查询，后果有两个：
        //   a) 类目清单被截到每页条数（默认 10 条），展开出的下级类目因此不完整，筛选结果偏少；
        //   b) 真正的图书查询完全失去分页，一页返回全部命中数据，分页器形同虚设。
        // 做法：把当前的 Page 取出来并清掉，让类目查询以「无分页」状态执行，
        // 结束后再放回去，使随后那条图书查询拿到本该属于它的分页参数。
        Page<?> page = PageHelper.getLocalPage();
        PageHelper.clearPage();
        try
        {
            List<BookCategory> all = bookCategoryService.findList();
            Set<Integer> ids = collectSelfAndDescendantIds(all, categoryId);
            return new ArrayList<>(ids);
        }
        finally
        {
            if (page != null)
            {
                PageHelper.setLocalPage(page);
            }
        }
    }

    /**
     * 查询图书，不存在时抛出业务异常
     *
     * @param id 图书ID
     * @return 图书信息
     */
    private BookVo requireBook(Integer id)
    {
        if (id == null)
        {
            throw new ServiceException("图书ID不能为空");
        }
        BookVo book = bookService.findById(id);
        if (book == null)
        {
            throw new ServiceException("图书不存在或已被删除");
        }
        return book;
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
     * 收集指定类目及其全部子孙类目的ID
     *
     * <p>类目为一次性全量加载，直接在内存中逐层展开，避免递归查询数据库。</p>
     *
     * @param all    全部类目
     * @param rootId 根类目ID
     * @return 自身及全部子孙类目ID集合
     */
    private Set<Integer> collectSelfAndDescendantIds(List<BookCategory> all, Integer rootId)
    {
        Set<Integer> result = new HashSet<>();
        if (rootId == null)
        {
            return result;
        }
        result.add(rootId);
        boolean expanded = true;
        while (expanded)
        {
            expanded = false;
            for (BookCategory category : all)
            {
                if (category.getParentId() != null && result.contains(category.getParentId())
                        && result.add(category.getId()))
                {
                    expanded = true;
                }
            }
        }
        return result;
    }
}
