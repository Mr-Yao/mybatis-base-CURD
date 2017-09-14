package me.ly.tools.mybatis.base;

import java.util.List;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import me.ly.tools.mybatis.dao.GeneralMapper;
import me.ly.tools.mybatis.entity.Pagination;

/**
 * 基础业务接口实现类
 *
 * @author Created by LiYao on 2017-03-03 22:38.
 */
@SuppressWarnings({ "all" })
@Service("baseServiceImpl")
public class BaseServiceImpl implements BaseService {

    @Autowired
    private GeneralMapper generalMapper;

    @Override
    public <T> T selectOne(Class<T> clazz, String where, Object... params) {
        List<T> list = this.selectAll(clazz, where, params);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public <T> T selectById(Class<T> clazz, Object id) {
        if (id == null) {
            return null;
        }
        List<T> list = generalMapper.selectById(clazz, id);
        if (list == null || list.isEmpty()) {
            return null;
        }
        return list.get(0);
    }

    @Override
    public <T> List<T> selectAll(Class<T> clazz, String where, Object... params) {
        return generalMapper.selectAll(clazz, where, params);
    }

    @Override
    public <T> List<T> selectByPage(Class<T> clazz, Pagination pagination, String where, Object... params) {
        return generalMapper.selectByPage(clazz, pagination, where, params);
    }

    @Override
    public <T> int insert(T obj) {
        return generalMapper.insert(obj);
    }

    @Override
    public <T> int insertList(List<T> list) {
        if (CollectionUtils.isEmpty(list)) {
            return 0;
        }
        return generalMapper.insertList(list);
    }

    @Override
    public <T> int delete(T obj) {
        return generalMapper.delete(obj);
    }

    @Override
    public <T> int update(T obj, boolean ignoreNull, boolean ignoreEmpty) {
        return generalMapper.update(obj, ignoreNull, ignoreEmpty);
    }

    @Override
    public <T> int update(T obj) {
        return this.update(obj, true, true);
    }

    @Override
    public <T> int updateList(List<T> objs, boolean ignoreNull, boolean ignoreEmpty) {
        int i = 0;
        if (CollectionUtils.isEmpty(objs)) {
            return i;
        }
        for (T obj : objs) {
            i += generalMapper.update(obj, ignoreNull, ignoreEmpty);
        }
        return i;
    }

    @Override
    public <T> int updateList(List<T> objs) {
        return this.updateList(objs, true, true);
    }

    @Override
    public int executeCUD(String sql, Object... param) {
        if (sql.toUpperCase().trim().startsWith("INSERT")) {
            return generalMapper.executeInsert(sql, param);
        }
        if (sql.toUpperCase().trim().startsWith("UPDATE")) {
            return generalMapper.executeUpdate(sql, param);
        }
        if (sql.toUpperCase().trim().startsWith("DELETE")) {
            return generalMapper.executeDelete(sql, param);
        }
        throw new IllegalArgumentException("非法的insert/update/delete语句");
    }
}
