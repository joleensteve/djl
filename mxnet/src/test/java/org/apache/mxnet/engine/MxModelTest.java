/*
 * Copyright 2019 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"). You may not use this file except in compliance
 * with the License. A copy of the License is located at
 *
 * http://aws.amazon.com/apache2.0/
 *
 * or in the "license" file accompanying this file. This file is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES
 * OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 */
package org.apache.mxnet.engine;
// CHECKSTYLE:OFF:AvoidStaticImport

import static org.powermock.api.mockito.PowerMockito.mockStatic;

import com.amazon.ai.ndarray.types.DataDesc;
import com.amazon.ai.ndarray.types.DataType;
import com.amazon.ai.util.Pair;
import com.amazon.ai.util.PairList;
import com.amazon.ai.util.Utils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.function.Function;
import org.apache.mxnet.jna.LibUtils;
import org.apache.mxnet.jna.MxnetLibrary;
import org.apache.mxnet.test.MockMxnetLibrary;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.testng.PowerMockTestCase;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

// CHECKSTYLE:ON:AvoidStaticImport

@PrepareForTest(LibUtils.class)
public class MxModelTest extends PowerMockTestCase {

    @BeforeClass
    public void prepare() {
        mockStatic(LibUtils.class);
        MxnetLibrary library = new MockMxnetLibrary();
        PowerMockito.when(LibUtils.loadLibrary()).thenReturn(library);
        Utils.deleteQuietly(Paths.get("build/tmp/testArt/"));
    }

    @AfterClass
    public void postProcess() {
        Utils.deleteQuietly(Paths.get("build/tmp/testArt/"));
    }

    @Test
    public void testLoadModel() throws IOException {
        String prefix = "A";
        int epoch = 122;
        MxModel model = MxModel.loadModel(prefix, epoch);
        Assert.assertEquals(model.getParameters().get(0).getKey(), "A-0122.params");
        Symbol sym = model.getSymbol();
        Assert.assertNotNull(sym);
        model.close();
    }

    @Test
    public void testDescribeInput() throws IOException {
        String prefix = "A";
        int epoch = 122;
        MxModel model = MxModel.loadModel(prefix, epoch);
        PairList<String, MxNDArray> pairs = model.getParameters();
        pairs.remove("A-0122.params");
        pairs.add(new Pair<>("a", null));
        DataDesc[] descs = model.describeInput();
        // Comparing between a, b, c to a, b, c, d, e
        Assert.assertEquals(descs[0].getName(), "d");
        Assert.assertEquals(descs[1].getName(), "e");
        DataDesc[] descs2 = model.describeInput();
        Assert.assertTrue(Arrays.equals(descs2, descs));
    }

    @Test
    public void testCast() throws IOException {
        String prefix = "A";
        int epoch = 122;
        MxModel model = MxModel.loadModel(prefix, epoch);
        MxModel casted = (MxModel) model.cast(DataType.FLOAT32);
        Assert.assertEquals(casted.getParameters(), model.getParameters());
        casted = (MxModel) model.cast(DataType.FLOAT64);
        Assert.assertEquals(
                casted.getParameters().get(0).getValue().getDataType(), DataType.FLOAT64);
    }

    @Test
    public void testGetArtifacts() throws IOException {
        String dir = "build/tmp/testArt/";
        String prefix = "A";
        int epoch = 122;
        // Test: Check filter
        Files.createDirectories(Paths.get(dir));
        Files.createFile(Paths.get(dir + prefix + "-0001.params"));
        Files.createFile(Paths.get(dir + prefix + "-symbol.json"));
        MxModel model = MxModel.loadModel(dir + prefix, epoch);
        Assert.assertEquals(model.getArtifactNames().length, 0);

        // Test: Add new file
        String synset = "synset.txt";
        Files.createFile(Paths.get(dir + synset));
        Assert.assertEquals(model.getArtifactNames()[0], synset);

        // Test: Add subDir
        Files.createDirectories(Paths.get(dir + "inner/"));
        Files.createFile(Paths.get(dir + "inner/innerFiles"));
        Assert.assertEquals(model.getArtifactNames()[0], "inner/innerFiles");

        // Test: Get Artifacts
        InputStream stream = model.getArtifactAsStream(synset);
        Assert.assertEquals(stream.available(), 0);
        Assert.assertNull(model.getArtifact("fileNotExist"));
        Assert.assertThrows(IllegalArgumentException.class, () -> model.getArtifact(null));
        // Test: Get Custom Artifacts
        Function<InputStream, String> wrongFunc =
                tempStream -> {
                    throw new RuntimeException("Test");
                };
        Assert.assertThrows(RuntimeException.class, () -> model.getArtifact(synset, wrongFunc));
        Function<InputStream, String> func = tempStream -> "Hello";
        String result = model.getArtifact(synset, func);
        Assert.assertEquals(result, "Hello");
        model.close();
    }
}
