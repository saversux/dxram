/*
 * Copyright (C) 2019 Heinrich-Heine-Universitaet Duesseldorf, Institute of Computer Science,
 * Department Operating Systems
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package de.hhu.bsinfo.dxram.loader;

import java.nio.file.Paths;

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
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER),
                @DXRAMTestConfiguration.Node(nodeRole = NodeRole.PEER)

        })
public class DistributedLoaderTest {
    @TestInstance(runOnNodeIdx = 1)
    public void initSuperpeer(final DXRAM p_instance) {
        LoaderService loaderService = p_instance.getService(LoaderService.class);
        loaderService.addJar(Paths.get("src/extTest/resources/dxrest-1.jar"));
    }

    @TestInstance(runOnNodeIdx = 2)
    public void simpleTest(final DXRAM p_instance) throws InterruptedException {
        Thread.sleep(100);
        LoaderService loaderService = p_instance.getService(LoaderService.class);

        Class test = null;
        try {
            test = loaderService.getClassLoader().loadClass("de.hhu.bsinfo.dxapp.rest.cmd.requests.AppRunRequest");
        } catch (ClassNotFoundException e) {
            Assert.fail("Oups, classloading failed.");
        }
        Assert.assertNotNull(test);

        try {
            loaderService.getClassLoader().loadClass("BestClass");
            Assert.fail("This class should not exist.");
        } catch (ClassNotFoundException e) {
            // this is nice
        }
    }
}