import { memo, useMemo } from 'react'
import { StyleSheet, View } from 'react-native'
import SubTitle from '../../components/SubTitle'
import CheckBox from '@/components/common/CheckBox'
import { useSettingValue } from '@/store/setting/hook'
import { updateSetting } from '@/core/common'

const SOURCE_LIST = [
  { id: 'kw', name: '酷我' },
  { id: 'kg', name: '酷狗' },
  { id: 'tx', name: 'QQ' },
  { id: 'wy', name: '网易' },
  { id: 'mg', name: '咪咕' },
  { id: 'bilibili', name: '哔哩' },
  { id: 'git', name: 'Git' },
  { id: 'all', name: '聚合' },
]

const Item = ({ id, name }: { id: string; name: string }) => {
  const enabledSources = useSettingValue('search.enabledSources')
  const isActive = useMemo(() => enabledSources[id] !== false, [enabledSources, id])
  return (
    <View style={styles.item}>
      <CheckBox
        marginRight={8}
        check={isActive}
        label={name}
        onChange={() => {
          const updated = { ...enabledSources, [id]: !isActive }
          updateSetting({ 'search.enabledSources': updated })
        }}
      />
    </View>
  )
}

export default memo(() => {
  const mid = Math.ceil(SOURCE_LIST.length / 2)
  const leftCol = SOURCE_LIST.slice(0, mid)
  const rightCol = SOURCE_LIST.slice(mid)

  return (
    <SubTitle title="启用搜索平台">
      <View style={styles.grid}>
        <View style={styles.column}>
          {leftCol.map((s) => (
            <Item id={s.id} name={s.name} key={s.id} />
          ))}
        </View>
        <View style={styles.column}>
          {rightCol.map((s) => (
            <Item id={s.id} name={s.name} key={s.id} />
          ))}
        </View>
      </View>
    </SubTitle>
  )
})

const styles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  column: {
    flex: 1,
  },
  item: {
    paddingVertical: 4,
  },
})
