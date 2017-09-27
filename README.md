# MyBatis基础 CRUD 封装
将基础的 增、删、改、查进行了封装。省掉大部分简单Mapper的编写，节省开发时间。采用标准JPA规范，在此处配置好的POJO，
放到其他任何实现了JPA的框架（如：Hibernate）都能使用。当然目前只使用到了JPA的一小部分注解，可以把这些封装看作一个超超超低配版的Hibernate。
目前支持的注解：
```$java
@Table              数据库表名映射。
@Id                 主键映射。
@Column             字段名映射。其中name、updatable、insertable属性均支持
@Transient          表示不需要跟数据库作映射。在我的封装中只有加了@Column才会同数据库映射，但在Hibernate中必须得使用。
@MappedSuperclass   实体之间继承关系
```
都是一些常用配置。做这个东西的初衷仅仅是为了简化单表的操作，什么@ManyToMany、@JoinTable直接忽略。子查询、关联查询、以及比较复杂的查询请写Mapper比较好。
## 配置
很简单的几步配置。主要是在`SqlSessionFactoryBean`中加入结果集处理插件和分页插件，以及`MapperScannerConfigurer`中增加`BaseMappper`
```$xml
省略部分代码......

<!--配置插件-->
<bean id="myBatisResultIntercept" class="me.ly.tools.mybatis.mybatis.MybatisResultInterceptor"/>
<bean id="mybatisPaginationInterceptor" class="me.ly.tools.mybatis.mybatis.MybatisPaginationInterceptor"/>

<bean id="sqlSessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
    <property name="dataSource" ref="dataSource"/>
    <property name="plugins">
        <list>
            <ref bean="mybatisPaginationInterceptor"/>
            <ref bean="myBatisResultIntercept"/>
        </list>
    </property>
</bean>
<!-- MapperScanner 中加上 BaseMapper -->
<bean class="org.mybatis.spring.mapper.MapperScannerConfigurer">
    <property name="basePackage" value="......,me.ly.tools.mybatis.dao"/>
    <property name="sqlSessionFactoryBeanName" value="sqlSessionFactory"/>
</bean>

省略部分代码......
```
## 使用
### 1、POJO添加注解
这里以区域表作为例子。下面贴出area表及其对应实体类。

|area_id|name|parent_id|province_id|city_id|level|create_by|create_date|
|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|:-------:|
|23|四川|-1|23| |1|system|2016-08-24|
|286|成都|23|23|286|2|system|2016-08-24|
|287|自贡|23|23|287|2|system|2016-08-24|
|288|攀枝花|23|23|288|2|system|2016-08-24|
|289|泸州|23|23|289|2|system|2016-08-24|


```$java
@Entity
@Table(name = "area")
public class AreaDO extends BaseDO {

    省略部分代码......
    
    @Id
    @Column(name = "area_id")
    private Integer areaId;

    @Column
    private String name;

    @Column(name = "parent_id")
    private Integer parentId;

    @Column(name = "city_id")
    private Integer cityId;

    @Column(name = "province_id")
    private Integer provinceId;
    
    省略getter/setter部分代码......
}
```
如果有继承关系，则需要在父类加上`@MappedSuperclass`。如上代码段中AreaDO extends BaseDO 
```$java
@MappedSuperclass
public class BaseDO implements Serializable {
    
    /** 创建人 */
    @Column(name = "create_by", updatable = false)
    private String createBy;

    /** 创建时间 */
    @Column(name = "create_date", updatable = false)
    private Date createDate;
        
    省略部分代码......
}

```
### 2、实现类继承BaseServiceImpl类
```$java
@Service
public class AreaServiceImpl extends BaseServiceImpl implements AreaService{
    省略部分代码......
    
    public void method(){
        AreaDO areaDo1 = this.selectOne(AreaDO.class,"name = ?","四川");
        System.out.println(JSON.toJSONString(areaDO1));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        AreaDO areaDO2 = this.selectById(AreaDO.class, 23);
        System.out.println(JSON.toJSONString(areaDO2));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        List<AreaDO> areaList = this.selectAll(AreaDO.class, "parent_id = ? LIMIT 2", 23);
        System.out.println(JSON.toJSONString(areaList));
        //输出：
        //  [
        //    {"areaId":286,"cityId":286,"createBy":"system","createDate":1471968000000,"name":"成都","parentId":23,"provinceId":23},
        //    {"areaId":287,"cityId":287,"createBy":"system","createDate":1471968000000,"name":"自贡","parentId":23,"provinceId":23}
        //  ]
    }    
}
```
### 3、注入BaseService
因为可能被其它类继承，所以这里注入时要限定名字。
```$java
@Service
public class AreaServiceImpl implements AreaService{
    @Autowired
    @Qualifier(value = "baseServiceImpl")
    private BaseService baseService;

    public void method(){
        AreaDO areaDo1 = this.baseService.selectOne(AreaDO.class,"name = ?","四川");
        System.out.println(JSON.toJSONString(areaDO1));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        AreaDO areaDO2 = this.baseService.selectById(AreaDO.class, 23);
        System.out.println(JSON.toJSONString(areaDO2));
        //输出：{"areaId":23,"cityId":0,"createBy":"system","createDate":1471968000000,"name":"四川","parentId":-1,"provinceId":23}
    
        List<AreaDO> areaList = this.baseService.selectAll(AreaDO.class, "parent_id = ? LIMIT 2", 23);
        System.out.println(JSON.toJSONString(areaList));
        //输出：
        //  [
        //    {"areaId":286,"cityId":286,"createBy":"system","createDate":1471968000000,"name":"成都","parentId":23,"provinceId":23},
        //    {"areaId":287,"cityId":287,"createBy":"system","createDate":1471968000000,"name":"自贡","parentId":23,"provinceId":23}
        //  ]
    }

}
```
## 代码片段
### 1、BaseService
BaseService 提供了基础增、删、改、查方法，供其它地方使用
```$java
省略部分代码......
<T> T selectOne(Class<T> clazz, String where, Object... params);

<T> List<T> selectAll(Class<T> clazz, String where, Object... params);

<T> T selectById(Class<T> clazz, Object id);

<T> List<T> selectByPage(Class<T> clazz, Pagination pagination, String where, Object... params);

<T> int insert(T obj);

<T> int insertList(List<T> list);

<T> int delete(T obj);

<T> int update(T obj);

<T> int update(T obj, boolean ignoreNull, boolean ignoreEmpty);

<T> int updateList(List<T> objs);

<T> int updateList(List<T> objs, boolean ignoreNull, boolean ignoreEmpty);

int executeCUD(String sql, Object... param);

省略部分代码......
```
### 2、CRUDTemplate
用于构造SQL，配合`BaseMapper`使用。
```$java
省略部分代码......

public String selectById(Map<?, ?> map) {
    SQL sql = new SQL();
    Class<?> clazz = (Class<?>) map.get("clazz");
    sql.SELECT("*").FROM(ReflectUtil.tableName(clazz));
    Object id = map.get("id");
    if (null == id || "".equals(id)) {
        return sql.toString();
    }
    Map<String, String> columnMap = ReflectUtil.id(clazz);
    sql.WHERE(columnMap.get(MyBatisUtil.KEY_ID_COLUMN) + " = #{id}");
    return sql.toString();
}
省略部分代码......
```
### 3、BaseMapper
通过`@SelectProvider`/`@InsertProvider`/`@DeleteProvider`/`@UpdateProvider` 将`CRUDTemplate`构建的SQL交由Mybatis做后续处理
```$java
省略部分代码......

@SelectProvider(type = CRUDTemplate.class, method = "selectById")
<T> List<T> selectById(@Param("clazz") Class<T> clazz, @Param("id") Object id);

省略部分代码......
```
### 4、MybatisResultInterceptor
用于处理查询结果集的拦截器。通过`JPA`注解将POJO字段同数据库中字段对应，再交由该拦截器处理。可以省掉很多ResultMap的编写。
注意事项：
1. 如某方法已经设置了ResultMap，则该方法会被拦截但是拦截后会直接交给MyBatis，拦截器本身不会再做处理。
2. 仅支持单表单实体，对于一些复杂的对象请使用`ResultMap`。
3. SQL查询字段需要和@Column设置的名字对应，使用别名的时候注意一下。
4. 返回对象必须设置`@Entity`注解，字段必须设置`@Column`注解。
5. 该拦截器中提供了`interceptAllMethod`字段，在声明插件时通过Spring注入（true/false），用于设置是否对所有方法拦截。默认true。  
同时提供了`@ResultIntercept`注解，来更加灵活的配置对方法的连接。该注解可以在方法或者接口类上使用。
    1. 如果在方法上配置了@ResultIntercept，则该方法一定会或不会被拦截。优先级最高
    2. 如果在接口类上配置了@ResultIntercept(intercept = false)，则该接口类的方法不会被拦截。优先级第二
    3. 如果设置了interceptAllMethod = true，则所有方法都会被拦截。优先级第三
    4. 如果设置了interceptAllMethod = false，则判断接口类上的@ResultIntercept。优先级最低
    
```
......
private boolean isIntercept(String fullMethodName) throws Exception {
    // 方法名为空，不拦截
    if (StringUtils.isBlank(fullMethodName)) {
        return false;
    }

    int lastPointIndex = fullMethodName.lastIndexOf(".");
    String fullClazz = fullMethodName.substring(0, lastPointIndex);
    String methodName = fullMethodName.substring(lastPointIndex + 1, fullMethodName.length());
    Class<?> clazz = Class.forName(fullClazz);

    Method method = ReflectUtil.getMethod(clazz, methodName);
    ResultIntercept methodAnnotation = method.getAnnotation(ResultIntercept.class);
    if (methodAnnotation != null) {
        return methodAnnotation.intercept();
    }
    ResultIntercept classAnnotation = clazz.getAnnotation(ResultIntercept.class);
    if (interceptAllMethod) {
        // 设置了拦截所有方法的时候要判断接口类是不是配置不拦截。
        return !(classAnnotation != null && !classAnnotation.intercept());
    }
    //未设置拦截所有方法，则判断接口类是不是配置了拦截
    return !(classAnnotation == null || !classAnnotation.intercept());
}
......
```
### 5、MybatisPaginationInterceptor
分页拦截器。`SELECT`语句且参数带`Pagination`的方法都会被拦截，拦截后会对SQL做一些包装，比如加上`Limit`
```$java
省略部分代码......

int fromIndex = originalSql.toUpperCase().indexOf("FROM");
String countSql = "SELECT COUNT(*) AS TOTAL " + originalSql.substring(fromIndex);
preparedStatement = connection.prepareStatement(countSql);

if (countSql.contains("?") && paramObj instanceof Map<?, ?>) {
    Map<?, ?> paramMap = (Map<?, ?>) paramObj;
    Object[] params = (Object[]) paramMap.get("params");
    if (params != null) {
        for (int i = 0; i < params.length; i++) {
            preparedStatement.setObject(i + 1, params[i]);
        }
    }

}
rs = preparedStatement.executeQuery();
if (rs.next()) {
    int totalRecord = rs.getInt("TOTAL");
    page.setTotalCount(totalRecord);
}
省略部分代码......

private String mysqlLimitPageSql(Pagination page, String sql) {
    StringBuilder sqlBuilder = new StringBuilder(sql);
    int offset = (page.getPageIndex() - 1) * page.getPageSize();
    sqlBuilder.append(" LIMIT ").append(offset).append(",").append(page.getPageSize());
    return sqlBuilder.toString();
}

省略部分代码......
```
## 不足之处
1、insertList生成的SQL（insert into table(c1,c2) values(1,2),(3,4)）无法通用。  
2、分页插件仅支持Mysql和Oracle  
3、会在代码中植入SQL语句，需要对组员进行规范。复杂SQL使用Mapper文件
## 数据库兼容性
使用mysql数据库进行的开发，所以都是根据mysql进行的开发。但是所有封装中除insertList外，其他都是用的标准SQL。所以理论上可以不用考虑数据库兼容性的问题，在批量插入时注意下就好。
# 结语
源码已经放到了GitHub，没啥难度，需要使用拷下来直接使用即可，就不写Demo了 。东西虽然不大，如果有什么问题、建议欢迎大家留言谈论，可以直接PR。

[https://github.com/Mr-Yao/mybatis-base-CURD](https://github.com/Mr-Yao/mybatis-base-CURD)
