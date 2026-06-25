<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  field: {
    type: Object,
    required: true
  },
  disabled: {
    type: Boolean,
    default: false
  },
  size: {
    type: String,
    default: 'normal'
  }
})

const emit = defineEmits(['update', 'action'])

const searchKeyword = ref('')
const remoteOptions = ref(null)
const fetchTimer = ref(null)

const config = computed(() => props.field.options || props.field.type_config || {})
const normalizedType = computed(() => {
  const component = props.field.component || props.field.type
  if (!component || component === 'input') {
    return 'text'
  }
  return component
})
const inputValue = computed(() => props.field.value ?? '')
const isDisabled = computed(() => props.disabled || props.field.disabled === true)
const isSelect = computed(() => normalizedType.value === 'select')
const isButton = computed(() => normalizedType.value === 'button')
const isTextarea = computed(() => ['textarea', 'json', 'markdown'].includes(normalizedType.value))
const isRadio = computed(() => normalizedType.value === 'radio')
const isCheckbox = computed(() => normalizedType.value === 'checkbox')
const isSwitch = computed(() => normalizedType.value === 'switch')
const isCustom = computed(() => normalizedType.value === 'custom')
const isNumber = computed(() => normalizedType.value === 'number')
const isDate = computed(() => normalizedType.value === 'date')
const isDatetime = computed(() => normalizedType.value === 'datetime')
const inputType = computed(() => {
  if (isNumber.value) return 'number'
  if (isDate.value) return 'date'
  if (isDatetime.value) return 'datetime-local'
  return 'text'
})
const placeholder = computed(() => config.value.placeholder || props.field.label || '')
const options = computed(() => config.value.masks || config.value.list || config.value.options || [])
const optionsSource = computed(() => Array.isArray(remoteOptions.value) ? remoteOptions.value : options.value)
const enableSearch = computed(() => config.value.enableSearch === true)
const searchKey = computed(() => config.value.searchKey || 'value')
const searchPlaceholder = computed(() => config.value.searchPlaceholder || '搜索')
const isMultiple = computed(() => config.value.multiple === true)
const placeholderOption = computed(() => isMultiple.value ? '' : config.value.placeholder || '')
const textareaRows = computed(() => config.value.rows || (normalizedType.value === 'json' ? 6 : 4))
const textareaStyle = computed(() => {
  if (!config.value.height) return {}
  return { height: typeof config.value.height === 'number' ? `${config.value.height}px` : config.value.height }
})
const showMarkdownPreview = computed(() => normalizedType.value === 'markdown' && config.value.preview === true)
const isClearable = computed(() => {
  if (isButton.value || isCustom.value || isCheckbox.value || isRadio.value || isSwitch.value) {
    return false
  }
  return config.value.clearable !== false
})
const showClear = computed(() => {
  if (!isClearable.value) return false
  if (isMultiple.value) {
    return Array.isArray(inputValue.value) && inputValue.value.length > 0
  }
  return inputValue.value !== '' && inputValue.value !== null && inputValue.value !== undefined
})
const buttonClass = computed(() => props.field.variant ? `btn-${props.field.variant}` : '')
const fieldClass = computed(() => ({
  'field--button': isButton.value,
  'field--textarea': isTextarea.value,
  'field--custom': isCustom.value,
  'field--compact': props.size === 'compact'
}))
const fieldStyle = computed(() => {
  if (!config.value.width) return {}
  return { width: typeof config.value.width === 'number' ? `${config.value.width}px` : config.value.width }
})
const customSlotKey = computed(() => config.value.slotKey || props.field.slotKey || 'custom-field')
const switchLabel = computed(() => inputValue.value ? config.value.onLabel || '开启' : config.value.offLabel || '关闭')
const displayValue = computed(() => {
  if (isDate.value || isDatetime.value) {
    return toNativeValue(inputValue.value)
  }
  return inputValue.value
})
const selectValue = computed(() => {
  if (isMultiple.value) {
    return Array.isArray(inputValue.value) ? inputValue.value : []
  }
  return inputValue.value
})
const filteredOptions = computed(() => {
  if (!enableSearch.value || !searchKeyword.value) {
    return optionsSource.value
  }
  const keyword = searchKeyword.value.toLowerCase()
  return optionsSource.value.filter((option) => {
    const normalized = normalizeOption(option)
    return String(normalized.label).toLowerCase().includes(keyword)
  })
})

watch(enableSearch, (next) => {
  if (next && searchKeyword.value) {
    triggerFetch()
  }
}, { immediate: true })

onMounted(() => {
  if (typeof config.value.fetchMethod === 'function') {
    fetchOptions()
  }
})

onBeforeUnmount(() => {
  if (fetchTimer.value) {
    clearTimeout(fetchTimer.value)
    fetchTimer.value = null
  }
})

function normalizeOption(option) {
  if (option && option.label !== undefined && option.value !== undefined) {
    return option
  }
  return {
    label: option?.name ?? option,
    value: option?.code ?? option
  }
}

function emitUpdate(value) {
  const oldValue = props.field.value
  emit('update', { value, oldValue })
  emit('action', {
    source: 'AppControl',
    action: props.field.action,
    key: props.field.key,
    value,
    oldValue,
    field: props.field
  })
}

function onInput(value) {
  if (isDate.value || isDatetime.value) {
    emitUpdate(toValueFormat(value))
    return
  }
  if (isNumber.value) {
    if (value === '') {
      emitUpdate('')
      return
    }
    const parsed = Number(value)
    if (!Number.isNaN(parsed)) {
      emitUpdate(parsed)
      return
    }
  }
  emitUpdate(value)
}

function onSelectChange(event) {
  if (isMultiple.value) {
    emitUpdate(Array.from(event.target.selectedOptions).map((option) => option.value))
    return
  }
  emitUpdate(event.target.value)
}

function onCheckboxChange(value, checked) {
  const nextValue = Array.isArray(inputValue.value) ? [...inputValue.value] : []
  if (checked) {
    if (!nextValue.includes(value)) nextValue.push(value)
  } else {
    const index = nextValue.indexOf(value)
    if (index >= 0) nextValue.splice(index, 1)
  }
  emitUpdate(nextValue)
}

function onSwitchChange(checked) {
  emitUpdate(checked)
}

function onAction() {
  emit('action', {
    source: 'AppControl',
    action: props.field.action,
    key: props.field.key,
    value: props.field.value,
    oldValue: props.field.value,
    field: props.field
  })
}

function onClear() {
  emitUpdate(isMultiple.value ? [] : '')
}

function onSearchInput(value) {
  searchKeyword.value = value
  triggerFetch()
}

function triggerFetch() {
  if (typeof config.value.fetchMethod !== 'function') return
  if (fetchTimer.value) clearTimeout(fetchTimer.value)
  fetchTimer.value = setTimeout(() => {
    fetchOptions(searchKeyword.value)
  }, 300)
}

function fetchOptions(keyword = '') {
  if (typeof config.value.fetchMethod !== 'function') return
  const params = { ...(config.value.params || {}) }
  if (enableSearch.value && keyword) {
    params[searchKey.value] = keyword
  }
  const result = config.value.fetchMethod(params)
  if (result && typeof result.then === 'function') {
    result.then((list) => {
      remoteOptions.value = Array.isArray(list) ? list : []
    }).catch(() => {
      remoteOptions.value = []
    })
    return
  }
  remoteOptions.value = Array.isArray(result) ? result : []
}

function onBlur() {
  if (normalizedType.value !== 'json' || !config.value.formatOnBlur || !inputValue.value) {
    return
  }
  try {
    const formatted = JSON.stringify(JSON.parse(inputValue.value), null, 2)
    if (formatted !== inputValue.value) {
      emitUpdate(formatted)
    }
  } catch {
    // ignore invalid JSON formatting
  }
}

function toNativeValue(value) {
  if (!value) return ''
  const nativeFormat = isDatetime.value ? 'yyyy-MM-ddTHH:mm' : 'yyyy-MM-dd'
  if (isNativeFormat(value)) return value
  const parts = parseByFormat(value, config.value.valueFormat || config.value.format || nativeFormat)
  return parts ? formatByParts(parts, nativeFormat) : value
}

function toValueFormat(value) {
  if (!value) return ''
  const nativeFormat = isDatetime.value ? 'yyyy-MM-ddTHH:mm' : 'yyyy-MM-dd'
  const parts = parseByFormat(value, nativeFormat)
  const targetFormat = config.value.valueFormat || config.value.format || nativeFormat
  return parts ? formatByParts(parts, targetFormat) : value
}

function isNativeFormat(value) {
  return isDatetime.value ? /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value) : /^\d{4}-\d{2}-\d{2}$/.test(value)
}

function parseByFormat(value, format) {
  if (!value || !format) return null
  const tokenMap = { yyyy: '(\\d{4})', MM: '(\\d{2})', dd: '(\\d{2})', HH: '(\\d{2})', mm: '(\\d{2})' }
  const tokens = []
  let pattern = format
  Object.entries(tokenMap).forEach(([token, regex]) => {
    if (pattern.includes(token)) {
      pattern = pattern.replace(token, regex)
      tokens.push(token)
    }
  })
  const match = value.match(new RegExp(`^${pattern}$`))
  if (!match) return null
  return tokens.reduce((acc, token, index) => {
    acc[token] = match[index + 1]
    return acc
  }, {})
}

function formatByParts(parts, format) {
  return format
    .replace('yyyy', parts.yyyy || '0000')
    .replace('MM', parts.MM || '01')
    .replace('dd', parts.dd || '01')
    .replace('HH', parts.HH || '00')
    .replace('mm', parts.mm || '00')
}
</script>

<template>
  <div class="field" :class="fieldClass" :style="fieldStyle">
    <template v-if="isSelect">
      <input
        v-if="enableSearch"
        class="select-search"
        type="text"
        :placeholder="searchPlaceholder"
        :value="searchKeyword"
        :disabled="isDisabled"
        @input="onSearchInput($event.target.value)"
      >
      <select :value="selectValue" :multiple="isMultiple" :disabled="isDisabled" @change="onSelectChange">
        <option v-if="placeholderOption" disabled value="">
          {{ placeholderOption }}
        </option>
        <option
          v-for="(option, index) in filteredOptions"
          :key="index"
          :value="normalizeOption(option).value"
        >
          {{ normalizeOption(option).label }}
        </option>
      </select>
    </template>

    <template v-else-if="isButton">
      <button type="button" :class="buttonClass" :disabled="isDisabled" @click="onAction">
        {{ field.label }}
      </button>
    </template>

    <template v-else-if="isTextarea">
      <textarea
        :value="inputValue"
        :rows="textareaRows"
        :placeholder="placeholder"
        :maxlength="config.maxLength"
        :disabled="isDisabled"
        :style="textareaStyle"
        @input="onInput($event.target.value)"
        @blur="onBlur"
      />
      <div v-if="showMarkdownPreview" class="markdown-preview">
        <pre>{{ inputValue }}</pre>
      </div>
    </template>

    <template v-else-if="isRadio">
      <div class="option-list">
        <label v-for="(option, index) in filteredOptions" :key="index" class="option-item">
          <input
            type="radio"
            :name="field.key"
            :value="normalizeOption(option).value"
            :checked="normalizeOption(option).value === inputValue"
            :disabled="isDisabled"
            @change="emitUpdate(normalizeOption(option).value)"
          >
          <span>{{ normalizeOption(option).label }}</span>
        </label>
      </div>
    </template>

    <template v-else-if="isCheckbox">
      <div class="option-list">
        <label v-for="(option, index) in filteredOptions" :key="index" class="option-item">
          <input
            type="checkbox"
            :value="normalizeOption(option).value"
            :checked="Array.isArray(inputValue) && inputValue.includes(normalizeOption(option).value)"
            :disabled="isDisabled"
            @change="onCheckboxChange(normalizeOption(option).value, $event.target.checked)"
          >
          <span>{{ normalizeOption(option).label }}</span>
        </label>
      </div>
    </template>

    <template v-else-if="isSwitch">
      <label class="switch">
        <input type="checkbox" :checked="!!inputValue" :disabled="isDisabled" @change="onSwitchChange($event.target.checked)">
        <span class="slider" />
        <span class="switch-label">{{ switchLabel }}</span>
      </label>
    </template>

    <template v-else-if="isCustom">
      <slot :name="customSlotKey" :field="field" :value="inputValue" :disabled="isDisabled">
        <div class="custom-placeholder">自定义区域</div>
      </slot>
    </template>

    <template v-else>
      <input
        :type="inputType"
        :value="displayValue"
        :placeholder="placeholder"
        :disabled="isDisabled"
        @input="onInput($event.target.value)"
      >
    </template>

    <button v-if="showClear" type="button" class="clear-btn" @click="onClear">
      ×
    </button>
  </div>
</template>

<style scoped>
.field {
  position: relative;
  min-width: 120px;
}

.field input,
.field select,
.field textarea,
.field button {
  width: 100%;
  border: 1px solid var(--stroke);
  border-radius: 10px;
  background: var(--control-bg);
  color: var(--text);
  font: inherit;
  outline: none;
  box-shadow: none;
  transition: border-color 0.18s ease, background-color 0.18s ease;
}

.field input,
.field select,
.field button {
  height: 34px;
  padding: 0 12px;
}

.field textarea {
  min-height: 92px;
  padding: 10px 12px;
  resize: vertical;
}

.field input:focus,
.field select:focus,
.field textarea:focus,
.field button:focus {
  outline: none;
  box-shadow: none;
  border-color: rgba(59, 130, 246, 0.42);
  background: #fff;
}

.field input:focus-visible,
.field select:focus-visible,
.field textarea:focus-visible,
.field button:focus-visible {
  outline: none;
  box-shadow: none;
}

.field--compact input,
.field--compact select,
.field--compact button {
  height: 30px;
}

.select-search {
  margin-bottom: 6px;
}

.clear-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 999px;
  padding: 0;
  background: transparent;
  color: var(--text-dim);
}

.field--textarea .clear-btn,
.field--custom .clear-btn {
  top: 12px;
  transform: none;
}

.option-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.option-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.slider {
  width: 34px;
  height: 20px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.45);
  position: relative;
}

.slider::after {
  content: '';
  position: absolute;
  top: 2px;
  left: 2px;
  width: 16px;
  height: 16px;
  border-radius: 999px;
  background: #fff;
}

.switch input:checked + .slider {
  background: var(--app-accent);
}

.switch input:checked + .slider::after {
  transform: translateX(14px);
}

.switch input {
  display: none;
}

.markdown-preview,
.custom-placeholder {
  margin-top: 8px;
  border: 1px dashed var(--stroke);
  border-radius: 10px;
  padding: 10px;
  color: var(--text-dim);
  background: var(--surface-bg-3);
}

.btn-ghost {
  background: transparent;
}
</style>
