if (sub.isFile && sub.name.endsWith(".giga")) {
                    val missionControl = Utility.readFromFile<MissionControl>(sub.absolutePath)
                    if (missionControl != null) {
                        if (missionControl.finished) {
                            if (!sub.delete()) {
                                Log.w(TAG, "Unable to delete .giga file: " + sub.path)
                            }
                            continue
                        }

                        missionControl.running = false
                        missionControl.recovered = true
                        insertMission(missionControl)
                    }
                }