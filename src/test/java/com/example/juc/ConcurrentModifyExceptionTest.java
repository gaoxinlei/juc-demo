package com.example.juc;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * 并发修改异常测试。
 * 本例正常。
 * 在业务代码中碰到一个转换错误，并尝试用此test复现，后经仔细调查，系因使用了浅拷贝引起。
 * 如
 */
public class ConcurrentModifyExceptionTest {

    private static  final Logger LOGGER = LoggerFactory.getLogger(ConcurrentModifyExceptionTest.class);
    private static class Content{
        int id;
        public Content(int id){
            this.id = id;
        }
    }

    private static class Wrapper{
        int id;
        public Wrapper(Content content){
            this.id = content.id;
        }
    }

    private List<Wrapper> contentList = new ArrayList<>();

    /**
     * 正常情况，不会出异常。
     */
    @Test
    public void testConcurrentModify(){
        List<Content> contentList = new ArrayList<>();
        contentList.add(new Content(1));
        contentList.add(new Content(2));
        contentList.forEach(content -> {
            this.contentList.add(new Wrapper(content));
        });
        LOGGER.info("content:{}",this.contentList);
    }


    private static class WrapperObj{
        private List<Wrapper> contentList;
        public List<Wrapper> getContentList(){
            return this.contentList;
        }
        public void setContentList(List<Wrapper> contentList){
            this.contentList = contentList;
        }
    }

    private static class ContentObj{
        private List<Content> contentList;

        public List<Content> getContentList() {
            return contentList;
        }

        public void setContentList(List<Content> contentList) {
            this.contentList = contentList;
        }
    }


    /**
     * 浅拷贝，拷贝过程因泛型擦出不会出异常，会打印浅拷贝成功。
     * 进行o2的contentList遍历时，因尝试将Content转化为Wrapper，必出ClassCastException。
     * 进行显式的将o1的contentList元素取出并包装并塞入o2的contentList时，必出ConcurrentModificationException,
     * 因浅拷贝和泛型擦除，造成o1,o2的contentList其实是一个，因此是对同一个list进行遍历时增加元素。
     *
     */
    @Test
    public void testConCurrentModifyException(){
        ContentObj o1 = new ContentObj();
        WrapperObj o2 = new WrapperObj();
        List<Content> contentList = new ArrayList<>();
        o1.setContentList(contentList);
        contentList.add(new Content(1));

        try {
            Method method = WrapperObj.class.getDeclaredMethod("setContentList",List.class);
            method.setAccessible(true);
            method.invoke(o2,o1.getContentList());
            LOGGER.info("浅拷贝成功");
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
        try{
            o2.getContentList().forEach(content->{
                LOGGER.info(""+content.id);
            });

        }catch (Exception e){
            LOGGER.error("使用o2 contentList发现类型错误:",e);
        }

        try{
            List<Wrapper> wList = o2.getContentList();
            List<Content> oList = o1.getContentList();
            oList.forEach(o->{wList.add(new Wrapper(o));});

        }catch (Exception e){
            LOGGER.error("因浅拷贝造成并发修改异常，wList,oList是一个元素，遍历时增加:",e);
        }

    }

}
