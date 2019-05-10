package de.hhu.bsinfo.dxram.loader;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.junit.Assert;
import org.junit.runner.RunWith;

import de.hhu.bsinfo.dxram.DXRAM;
import de.hhu.bsinfo.dxram.DXRAMJunitRunner;
import de.hhu.bsinfo.dxram.DXRAMTestConfiguration;
import de.hhu.bsinfo.dxram.TestInstance;
import de.hhu.bsinfo.dxram.util.NodeRole;

@RunWith(DXRAMJunitRunner.class)
@DXRAMTestConfiguration(
        nodes = {
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.SUPERPEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.SUPERPEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER)
        })
public class LoaderSyncTest {
    @TestInstance(runOnNodeIdx = 2)
    public void register(final DXRAM p_instance) {
        LoaderService loaderService = p_instance.getService(LoaderService.class);
        loaderService.addJar(Paths.get("src/extTest/resources/dxrest-1.jar"));
    }

    @TestInstance(runOnNodeIdx = 0)
    public void check0(final DXRAM p_instance) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        LoaderService loaderService = p_instance.getService(LoaderService.class);
        Assert.assertEquals(4, loaderService.numberLoadedEntries());
    }

    @TestInstance(runOnNodeIdx = 1)
    public void check1(final DXRAM p_instance) throws InterruptedException {
        TimeUnit.SECONDS.sleep(1);
        LoaderService loaderService = p_instance.getService(LoaderService.class);
        Assert.assertEquals(4, loaderService.numberLoadedEntries());

        loaderService.flushTable();
        loaderService.sync();
        while(loaderService.numberLoadedEntries() != 4) {
            Thread.yield();
        }
        Assert.assertEquals(4, loaderService.numberLoadedEntries());
    }

}
