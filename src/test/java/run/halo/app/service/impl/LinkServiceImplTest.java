package run.halo.app.service.impl;



import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Sort;
import run.halo.app.model.entity.Link;
import run.halo.app.model.vo.LinkTeamVO;
import run.halo.app.service.LinkService;

@SpringBootTest
class LinkServiceImplTest {

    @Autowired
    LinkService linkService;


    @BeforeEach
    void before() {
        linkService.removeAll();
    }


    @Test
    void testListTeamVos1() {
        final Link link = new Link();
        link.setName("test");
        link.setUrl("https://www.baidu.com");
        linkService.create(link);
        final List<LinkTeamVO> result = linkService.listTeamVos(Sort.by("priority"));
        assertFalse(result.isEmpty());
        linkService.removeById(link.getId());
    }


    @Test
    void testListTeamVos2() {
        final Link link1 = new Link();
        link1.setName("1");
        link1.setUrl("https://www.baidu.com/1");
        link1.setTeam("group1");
        link1.setPriority(3);
        final Link link2 = new Link();
        link2.setName("2");
        link2.setUrl("https://www.baidu.com/2");
        link2.setTeam("group2");
        link2.setPriority(2);
        final Link link3 = new Link();
        link3.setName("3");
        link3.setUrl("https://www.baidu.com/3");
        link3.setTeam("group3");
        link3.setPriority(1);
        final Link link4 = new Link();
        link4.setName("4");
        link4.setUrl("https://www.baidu.com/3");
        link4.setTeam("group3");
        link4.setPriority(4);
        linkService.create(link1);
        linkService.create(link2);
        linkService.create(link3);
        linkService.create(link4);
        final List<LinkTeamVO> result = linkService.listTeamVos(Sort.by("name"));

        assertEquals(3, result.size());
        assertEquals("group3", result.get(0).getTeam());
        assertEquals(2, result.get(0).getLinks().size());
        assertEquals("group1", result.get(1).getTeam());
        assertEquals("group2", result.get(2).getTeam());

        linkService.removeById(link1.getId());
        linkService.removeById(link2.getId());
        linkService.removeById(link3.getId());
        linkService.removeById(link4.getId());
    }


    @Test
    void testListAllTeams1() {
        final Link link1 = new Link();
        link1.setName("1");
        link1.setUrl("https://www.baidu.com/1");
        link1.setTeam("group1");
        link1.setPriority(1);
        final Link link2 = new Link();
        link2.setName("2");
        link2.setUrl("https://www.baidu.com/2");
        link2.setTeam("group2");
        link2.setPriority(2);
        final Link link3 = new Link();
        link3.setName("3");
        link3.setUrl("https://www.baidu.com/3");
        link3.setTeam("group3");
        link3.setPriority(3);
        linkService.create(link1);
        linkService.create(link2);
        linkService.create(link3);
        final List<String> teams = linkService.listAllTeams();

        assertEquals(3, teams.size());
        assertEquals("group3", teams.get(0));
        assertEquals("group2", teams.get(1));
        assertEquals("group1", teams.get(2));

        linkService.removeById(link1.getId());
        linkService.removeById(link2.getId());
        linkService.removeById(link3.getId());
    }


    @Test
    void testListAllTeams2() {
        final Link link1 = new Link();
        link1.setName("1");
        link1.setUrl("https://www.baidu.com/1");
        link1.setTeam("group1");
        link1.setPriority(1);
        final Link link2 = new Link();
        link2.setName("2");
        link2.setUrl("https://www.baidu.com/2");
        link2.setTeam("group2");
        link2.setPriority(1);
        final Link link3 = new Link();
        link3.setName("3");
        link3.setUrl("https://www.baidu.com/3");
        link3.setTeam("group2");
        link3.setPriority(3);
        linkService.create(link1);
        linkService.create(link2);
        linkService.create(link3);
        final List<String> teams = linkService.listAllTeams();

        assertEquals(2, teams.size());
        assertEquals("group2", teams.get(0));
        assertEquals("group1", teams.get(1));

        linkService.removeById(link1.getId());
        linkService.removeById(link2.getId());
        linkService.removeById(link3.getId());
    }


}