/*
 * Copyright 2012-2013 Continuuity,Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.continuuity.weave.internal.utils;

import com.google.common.collect.ImmutableList;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public final class PathsTest {

  @Test
  public void testPath() {
    Assert.assertEquals("a:b:c", Paths.getClassPath(ImmutableList.of("a", "b", "c")));
    Assert.assertEquals("a:b:c", Paths.getClassPath(ImmutableList.of("a ", "  b  ", "  c")));
    Assert.assertEquals("a:b:c", Paths.getClassPath(ImmutableList.of("a ", "  b  \n", "\n  c")));
    Assert.assertEquals("a b:a b:a c", Paths.getClassPath(ImmutableList.of("a b ", "  a b  \n", "\n  a c")));
  }
}