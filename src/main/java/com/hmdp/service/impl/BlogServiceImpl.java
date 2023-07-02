package com.hmdp.service.impl;

import aj.org.objectweb.asm.ConstantDynamic;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.boot.jta.atomikos.AtomikosProperties;
import org.springframework.data.redis.connection.ReactiveSubscription;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;
    @Override
    public Result queryHotBlog(Integer current) {
//        根据用户查询
        Page<Blog> page= query().
                orderByDesc("licked").
                page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
//        获取当前页数据
        List<Blog> records=page.getRecords();
//        查询用户
        records.forEach(blog ->{
            this.queryBlogUser(blog);
            this.isBlogLiked(blog);
        });
        return Result.ok(records);

    }

    @Override
    public Result queryBlogById(Long id) {
//        查询blog
        Blog blog = getById(id);
        if(blog==null){
            return Result.fail("笔记不存在");
        }
//        查询blog有关的用户
        queryBlogUser(blog);
//        查询blog是否被点赞
        isBlogLiked(blog);
        return Result.ok(blog);

    }

    private void isBlogLiked(Blog blog) {
//        1.获取登陆有用户
        UserDTO user = UserHolder.getUser();
        if (user==null){
//            用户未登陆，无须查询是否点赞
            return;
        }
        Long userId = user.getId();
//        Long userId= UserHolder.getUser().getId();
//        2.判断单亲登陆用户是否已经点赞
        String key="blog:liked:"+blog.getId();
//        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
        Double score=stringRedisTemplate.opsForZSet().score(key,userId.toString());
//        blog.setIsLike(BooleanUtil.isTrue(isMember));
        blog.setIsLike(score!=null);

    }

    @Override
    public Result likeBlog(Long id) {
//        1.获取登陆用户
        Long userId = UserHolder.getUser().getId();
//        2.判断当前登陆用户是否已经点赞
        String  key="blog:liked:"+id;
        Double score = stringRedisTemplate.opsForZSet().score(key,userId.toString());
        if(score==null){
//        3.如果未点赞，可以点赞
//        3.1数据库点赞数+1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
//        3.2保存用户到Redis的set集合
            if(isSuccess){
                stringRedisTemplate.opsForZSet().add(key,userId.toString(),System.currentTimeMillis());
            }
        }else {
//        4.如果已经点赞，取消点赞
//        4.1数据库点赞数-1
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
//        4.2把用户从redis的set集合移除
            if(isSuccess){
                stringRedisTemplate.opsForZSet().remove(key,userId.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key=BLOG_LIKED_KEY+id;
//        1.查询top5的点赞用户zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if(top5==null||top5.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
//        2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
//        3.根据用户id查询用户where id in(5,1) order by field(id,5,1)
//        List<User> users = userService.listByIds(ids);
//       List<UserDTO> userDTOS = userService.listByIds(ids)
        List<UserDTO> userDTOS = userService.query().in("id",ids)
                .last("ORDER BY FIELD(id,5,1)")
                .list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
               .collect(Collectors.toList());
//        4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败");
        }
        //查询笔记作者的所有粉丝
        List<Follow> follows = followService.lambdaQuery()
                .eq(Follow::getFollowUserId, user.getId())
                .list();
        //推送笔记给所有粉丝
        for (Follow follow : follows) {
            Long userId = follow.getUserId();
            //推送
            String key="feed:"+userId;
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        //获取当前用户
        UserDTO user = UserHolder.getUser();
        //查询收件箱
        String key="feed:"+user.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);
        //非空判断
        if (typedTuples==null||typedTuples.isEmpty()){
            return Result.ok();
        }
        //解析数据 blogId minTime offset
        List<Long> ids=new ArrayList<>(typedTuples.size());
        long minTime =0;
        int os=1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
//            String blogId = typedTuple.getValue();
//            ids.add(Long.valueOf(blogId));
            ids.add(Long.valueOf(typedTuple.getValue()));
            long time = typedTuple.getScore().longValue();
            if (time==minTime){
                os++;
            }else {
                minTime = time;
                os=1;
            }
        }
        //根据 查询blog
        List<Blog> blogs=new ArrayList<>(ids.size());
        for (Long id : ids) {
            Blog blog = getById(id);
            blogs.add(blog);
        }
        blogs.forEach(this::isBlogLiked);
        //封装 返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
