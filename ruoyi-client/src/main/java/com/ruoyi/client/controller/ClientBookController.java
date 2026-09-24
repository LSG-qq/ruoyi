package com.ruoyi.client.controller;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.system.biz.ssk.BookBiz;
import com.ruoyi.system.biz.ssk.BookCategoryBiz;
import com.ruoyi.system.domain.Book;
import com.ruoyi.system.domain.BookCategory;
import com.ruoyi.system.domain.vo.ClientBookCategoryVo;
import com.ruoyi.system.domain.vo.ClientBookVo;

/**
 * 终端 - 图书浏览
 *
 * <p>囚犯在终端上只能「看」图书，不能新增、修改、删除，因此这里只有查询接口。
 * 接口一律不挂 {@code @PreAuthorize}：囚犯角色没有任何权限位，
 * 挂上去等于把所有终端用户挡在门外。</p>
 *
 * <p><b>本控制器的三个查询接口（列表、详情、类目）都标记了 {@link Anonymous}，即无需登录即可访问。</b>
 * 终端首页是「先浏览、后登录」的形态：未登录的人可以看类目树、图书列表与图书详情，
 * 点「申请借阅」时才被引导去登录。标记方式用的是若依自带的注解而不是在
 * SecurityConfig 里硬编码路径 —— 前者由 {@code PermitAllUrlProperties} 在启动时
 * 自动收集成放行清单，接口挪了位置或改了路径，放行规则跟着走，不会留下
 * 一条指向已不存在路径的死配置。</p>
 *
 * <p>放开的是**只读**接口，且数据本身是馆藏公开信息（书名、作者、书架号、可借数量）。
 * 借阅申请、我的申请、我的借阅等写接口与个人数据接口**仍然要求登录**，
 * 见 {@code ClientBorrowRequestController} / {@code ClientBorrowRecordController} ——
 * 匿名调用它们会拿到 401，而不是「按囚号显示为空」。</p>
 *
 * <p>返回体用 {@link ClientBookVo} 而不是后台的 BookVo，把审计字段挡在响应之外，
 * 详见该类注释。</p>
 *
 * @author ruoyi
 */
@RestController
@RequestMapping("/client/book")
public class ClientBookController extends BaseController
{
    @Autowired
    private BookBiz bookBiz;

    @Autowired
    private BookCategoryBiz bookCategoryBiz;

    /**
     * 分页查询图书列表（浏览）
     *
     * <p>查询条件与管理后台一致（书名、作者、书架号、类目），分页参数由 startPage() 从请求里取。
     * 类目筛选传入的是「选中类目 + 其全部下级类目」，展开在业务层完成。</p>
     *
     * <p>免登录：终端首页未登录时也要能浏览图书（见类注释）。</p>
     *
     * @param book       查询条件（name / author / shelfCode），可为空
     * @param categoryId 选中的类目ID，为空或 0 表示「全部」
     * @return 分页结果
     */
    @Anonymous
    @GetMapping("/list")
    public TableDataInfo list(Book book, @RequestParam(value = "categoryId", required = false) Integer categoryId)
    {
        startPage();
        return getDataTable(bookBiz.findClientPage(book, categoryId));
    }

    /**
     * 查询图书详情（终端浏览用）
     *
     * <p>点击列表里的一本图书时展示：书名、作者、简介，以及图片集组成的轮播图。
     * 列表接口本身已经带上了这些字段，但仍然单开一个详情接口，原因是列表是分页的
     * 快照数据 —— 用户停在列表页期间这本书可能已被管理员改过（改了简介、换了图）
     * 或被逻辑删除，详情接口每次都读当前库里的值，不至于展示一份过期内容。</p>
     *
     * <p>免登录：详情与列表同属「馆藏公开信息」，未登录也要能点开看（见类注释）。</p>
     *
     * @param id 图书ID
     * @return 图书详情（仅终端可见字段，见 {@link ClientBookVo}）
     */
    @Anonymous
    @GetMapping("/detail/{id}")
    public AjaxResult detail(@PathVariable("id") Integer id)
    {
        return success(bookBiz.findClientById(id));
    }

    /**
     * 查询图书类目列表（扁平结构，前端自行组装成树）
     *
     * <p>终端要按类目筛选图书，就得先有类目清单。返回扁平列表而不是已组装的树，
     * 与后台图书页的做法保持一致：前端用若依自带的 handleTree 组装。</p>
     *
     * <p>免登录：终端首页的类目树要在登录前就能渲染出来（见类注释）。</p>
     *
     * @return 类目列表
     */
    @Anonymous
    @GetMapping("/categoryList")
    public AjaxResult categoryList()
    {
        List<ClientBookCategoryVo> result = new ArrayList<>();
        for (BookCategory source : bookCategoryBiz.findList())
        {
            result.add(toClientCategory(source));
        }
        return success(result);
    }

    /**
     * 把类目裁剪为终端可见字段
     *
     * @param source 含审计字段的类目
     * @return 仅含终端可见字段的类目
     */
    private ClientBookCategoryVo toClientCategory(BookCategory source)
    {
        ClientBookCategoryVo target = new ClientBookCategoryVo();
        target.setId(source.getId());
        target.setParentId(source.getParentId());
        target.setTitle(source.getTitle());
        target.setOrderNum(source.getOrderNum());
        return target;
    }
}
