import { updateSetting as mergeSetting } from '@/config/setting'
import state from './state'
import { storageDataPrefix } from '@/config/constant'
import { saveData } from '@/plugins/storage'

export default {
  initSetting(newSetting: LX.AppSetting) {
    state.setting = newSetting
  },
  updateSetting(newSetting: Partial<LX.AppSetting>) {
    const result = mergeSetting(newSetting)
    state.setting = result.setting
    global.state_event.configUpdated(result.updatedSettingKeys, result.updatedSetting)
    // 立即保存设置，确保退出应用前数据不会丢失
    void saveData(storageDataPrefix.setting, state.setting)
  },
}
