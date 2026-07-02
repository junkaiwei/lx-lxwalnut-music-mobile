import { updateSetting } from '@/core/common'
import { createStyle } from '@/utils/tools'
import { memo } from 'react'
import { View } from 'react-native'
import { useSettingValue } from '@/store/setting/hook'
import { setScreenOrientation } from '@/utils/nativeModules/utils'

import CheckBoxItem from '../../components/CheckBoxItem'

export default memo(() => {
  const lockLandscape = useSettingValue('common.lockLandscape')

  const setLockLandscape = (value: boolean) => {
    updateSetting({ 'common.lockLandscape': value })
    setScreenOrientation(value ? 'landscape' : 'portrait')
  }

  return (
    <View style={styles.content}>
      <CheckBoxItem
        check={lockLandscape}
        label="横屏模式"
        onChange={setLockLandscape}
      />
    </View>
  )
})

const styles = createStyle({
  content: {
    marginTop: 5,
  },
})
