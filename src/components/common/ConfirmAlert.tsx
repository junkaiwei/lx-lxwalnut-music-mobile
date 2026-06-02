import { forwardRef, useImperativeHandle, useRef, useState, useCallback } from 'react'
import { View, ScrollView, PanResponder, Animated } from 'react-native'
import Dialog, { type DialogType } from './Dialog'
import Button from './Button'
import { createStyle } from '@/utils/tools'
import { useI18n } from '@/lang/index'
import { useTheme } from '@/store/theme/hook'
import Text from './Text'

const styles = createStyle({
  main: {
    flex: 1,
    marginTop: 15,
    marginLeft: 5,
    marginRight: 5,
    marginBottom: 25,
    flexDirection: 'row',
  },
  content: {
    flex: 1,
  },
  contentContainer: {
    paddingLeft: 10,
    paddingRight: 12,
  },
  scrollBarContainer: {
    width: 28,
    justifyContent: 'center',
    alignItems: 'center',
    paddingVertical: 4,
  },
  scrollBarTrack: {
    width: 28,
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  scrollBarTrackLine: {
    width: 8,
    height: '100%',
    borderRadius: 4,
    backgroundColor: 'rgba(0, 0, 0, 0.2)',
  },
  scrollBarThumb: {
    position: 'absolute',
    width: 8,
    height: 30,
    borderRadius: 4,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
  },
  btns: {
    flexDirection: 'row',
    justifyContent: 'center',
    paddingBottom: 15,
  },
  btnsDirection: {
    paddingLeft: 15,
  },
  btnsReversedDirection: {
    paddingLeft: 15,
    flexDirection: 'row-reverse',
  },
  btn: {
    flex: 1,
    paddingTop: 9,
    paddingBottom: 9,
    paddingLeft: 10,
    paddingRight: 10,
    alignItems: 'center',
    borderRadius: 4,
  },
  btnDirection: {
    marginRight: 15,
  },
  btnReversedDirection: {
    marginLeft: 15,
  },
})

export interface ConfirmAlertProps {
  onCancel?: () => void
  onHide?: () => void
  onConfirm?: () => void
  keyHide?: boolean
  bgHide?: boolean
  closeBtn?: boolean
  title?: string
  text?: string
  cancelText?: string
  confirmText?: string
  showConfirm?: boolean
  disabledConfirm?: boolean
  reverseBtn?: boolean
  children?: React.ReactNode | React.ReactNode[]
  middleText?: string
  onMiddle?: () => void
  showMiddle?: boolean
}

export interface ConfirmAlertType {
  setVisible: (visible: boolean) => void
}

export default forwardRef<ConfirmAlertType, ConfirmAlertProps>(
  (
    {
      onHide,
      onCancel,
      onConfirm = () => {},
      keyHide,
      bgHide,
      closeBtn,
      title = '',
      text = '',
      cancelText = '',
      confirmText = '',
      showConfirm = true,
      disabledConfirm = false,
      children,
      reverseBtn = false,
      middleText = '',
      onMiddle = () => {},
      showMiddle = false,
    }: ConfirmAlertProps,
    ref
  ) => {
    const theme = useTheme()
    const t = useI18n()

    const dialogRef = useRef<DialogType>(null)
    const scrollViewRef = useRef<ScrollView>(null)
    const scrollBarTrackRef = useRef<View>(null)
    
    const [trackHeight, setTrackHeight] = useState(0)
    const [contentHeight, setContentHeight] = useState(0)
    const [containerHeight, setContainerHeight] = useState(0)
    const [thumbPosition, setThumbPosition] = useState(new Animated.Value(0))
    const startYRef = useRef(0)
    const startPositionRef = useRef(0)

    const calculateTrackRatio = useCallback(() => {
      const scrollableHeight = Math.max(0, contentHeight - containerHeight)
      if (scrollableHeight <= 0) return 0
      const availableTrackHeight = trackHeight - Math.max(30, Math.min(trackHeight * 0.15, trackHeight))
      return availableTrackHeight / scrollableHeight
    }, [contentHeight, containerHeight, trackHeight])

    const calculateThumbHeight = useCallback(() => {
      return Math.max(30, Math.min(trackHeight * 0.15, trackHeight))
    }, [trackHeight])

    const calculateMaxThumbPosition = useCallback(() => {
      return trackHeight - calculateThumbHeight()
    }, [trackHeight, calculateThumbHeight])

    useImperativeHandle(ref, () => ({
      setVisible(visible: boolean) {
        dialogRef.current?.setVisible(visible)
        if (visible) {
          setTimeout(() => {
            thumbPosition.setValue(0)
            scrollViewRef.current?.scrollTo({ y: 0, animated: false })
          }, 100)
        }
      },
    }))

    const handleCancel = () => {
      onCancel?.()
      dialogRef.current?.setVisible(false)
    }

    const onScroll = useCallback((event: { nativeEvent: { contentOffset: { y: number } } }) => {
      const scrollY = event.nativeEvent.contentOffset.y
      const ratio = calculateTrackRatio()
      if (ratio > 0) {
        const targetPosition = scrollY * ratio
        thumbPosition.setValue(targetPosition)
      }
    }, [calculateTrackRatio, thumbPosition])

    const onContentSizeChange = useCallback((_width: number, height: number) => {
      setContentHeight(height)
    }, [])

    const onContainerLayout = useCallback((event: { nativeEvent: { layout: { height: number } } }) => {
      setContainerHeight(event.nativeEvent.layout.height)
    }, [])

    const onTrackLayout = useCallback((event: { nativeEvent: { layout: { height: number } } }) => {
      setTrackHeight(event.nativeEvent.layout.height)
    }, [])

    const panResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onPanResponderGrant: (_, gestureState) => {
        startYRef.current = gestureState.y0
        startPositionRef.current = thumbPosition._value
      },
      onPanResponderMove: (_, gestureState) => {
        const deltaY = gestureState.dy
        const newPosition = Math.max(0, Math.min(startPositionRef.current + deltaY, calculateMaxThumbPosition()))
        thumbPosition.setValue(newPosition)
        const ratio = calculateTrackRatio()
        if (ratio > 0) {
          const scrollY = newPosition / ratio
          scrollViewRef.current?.scrollTo({ y: scrollY, animated: false })
        }
      },
      onPanResponderRelease: () => {},
      onPanResponderTerminate: () => {},
    })

    const trackPanResponder = PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onPanResponderGrant: (_, gestureState) => {
        const newPosition = Math.max(0, Math.min(gestureState.y0 - 0 - calculateThumbHeight() / 2, calculateMaxThumbPosition()))
        thumbPosition.setValue(newPosition)
        const ratio = calculateTrackRatio()
        if (ratio > 0) {
          const scrollY = newPosition / ratio
          scrollViewRef.current?.scrollTo({ y: scrollY, animated: false })
        }
        startYRef.current = gestureState.y0
        startPositionRef.current = newPosition
      },
      onPanResponderMove: (_, gestureState) => {
        const deltaY = gestureState.dy
        const newPosition = Math.max(0, Math.min(startPositionRef.current + deltaY, calculateMaxThumbPosition()))
        thumbPosition.setValue(newPosition)
        const ratio = calculateTrackRatio()
        if (ratio > 0) {
          const scrollY = newPosition / ratio
          scrollViewRef.current?.scrollTo({ y: scrollY, animated: false })
        }
      },
      onPanResponderRelease: () => {},
      onPanResponderTerminate: () => {},
    })

    return (
      <Dialog
        onHide={onHide}
        keyHide={keyHide}
        bgHide={bgHide}
        closeBtn={closeBtn}
        title={title}
        ref={dialogRef}
        height="85%"
      >
        <View style={styles.main}>
          <View style={styles.content} onLayout={onContainerLayout}>
            <View style={{ flex: 1 }} onStartShouldSetResponder={() => true} onResponderTerminationRequest={() => false}>
              <ScrollView 
                ref={scrollViewRef}
                style={{ flex: 1 }}
                contentContainerStyle={styles.contentContainer}
                keyboardShouldPersistTaps={'always'}
                showsVerticalScrollIndicator={false}
                scrollEnabled={false}
                onScroll={onScroll}
                onContentSizeChange={onContentSizeChange}
                scrollEventThrottle={16}
              >
                {children ?? <Text>{text}</Text>}
              </ScrollView>
            </View>
          </View>
          <View style={styles.scrollBarContainer}>
            <View 
              ref={scrollBarTrackRef} 
              style={styles.scrollBarTrack}
              onLayout={onTrackLayout}
              {...trackPanResponder.panHandlers}
            >
              <View style={{ ...styles.scrollBarTrackLine, backgroundColor: theme['c-primary-dark-100-alpha-300'] }} />
              <Animated.View
                {...panResponder.panHandlers}
                style={{
                  ...styles.scrollBarThumb,
                  backgroundColor: theme['c-primary-light-500'],
                  top: thumbPosition,
                  height: calculateThumbHeight(),
                }}
              />
            </View>
          </View>
        </View>
        <View
          style={{
            ...styles.btns,
            ...(reverseBtn ? styles.btnsReversedDirection : styles.btnsDirection),
          }}
        >
          <Button
            style={{
              ...styles.btn,
              ...(reverseBtn ? styles.btnReversedDirection : styles.btnDirection),
              backgroundColor: theme['c-button-background'],
            }}
            onPress={handleCancel}
          >
            <Text color={theme['c-button-font']}>{cancelText || t('cancel')}</Text>
          </Button>
          {showMiddle ? (
            <Button
              style={{
                ...styles.btn,
                ...(reverseBtn ? styles.btnReversedDirection : styles.btnDirection),
                backgroundColor: theme['c-button-background'],
              }}
              onPress={onMiddle}
            >
              <Text color={theme['c-button-font']}>{middleText}</Text>
            </Button>
          ) : null}
          {showConfirm ? (
            <Button
              style={{
                ...styles.btn,
                ...(reverseBtn ? styles.btnReversedDirection : styles.btnDirection),
                backgroundColor: theme['c-button-background'],
              }}
              onPress={onConfirm}
              disabled={disabledConfirm}
            >
              <Text color={theme['c-button-font']}>{confirmText || t('confirm')}</Text>
            </Button>
          ) : null}
        </View>
      </Dialog>
    )
  }
)
