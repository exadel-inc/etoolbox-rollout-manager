/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.exadel.etoolbox.rolloutmanager.core.models;

public class RolloutItem {
    private String master;
    private String target;
    private int depth;
    boolean autoRolloutTrigger;

    public String getMaster() {
        return master;
    }

    public String getTarget() {
        return target;
    }

    public int getDepth() {
        return depth;
    }

    public boolean isAutoRolloutTrigger() {
        return autoRolloutTrigger;
    }
}
