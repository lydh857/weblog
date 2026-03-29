import type { LoginModalMode } from '~/composables/modal/useLoginModal'

export interface LoginFormState {
  email: string
  password: string
  confirmPassword: string
  nickname: string
  code: string
}

export interface LoginFormErrors {
  email: string
  password: string
  confirmPassword: string
  code: string
}

export interface ResetPasswordFormState {
  email: string
  code: string
  password: string
  confirmPassword: string
}

export interface ResetPasswordFormErrors {
  email: string
  code: string
  password: string
  confirmPassword: string
}

interface UseLoginModalFormValidationOptions {
  mode: Readonly<Ref<LoginModalMode>>
  form: LoginFormState
  errors: LoginFormErrors
  resetForm: ResetPasswordFormState
  resetErrors: ResetPasswordFormErrors
}

export function useLoginModalFormValidation(options: UseLoginModalFormValidationOptions) {
  function validateField(field: 'email' | 'password' | 'confirmPassword') {
    const { form, errors } = options

    if (field === 'email') {
      if (!form.email) {
        errors.email = '请输入邮箱'
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
        errors.email = '请输入有效的邮箱地址'
      } else {
        errors.email = ''
      }
    }

    if (field === 'password') {
      if (options.mode.value === 'code') {
        errors.password = ''
        return
      }

      if (!form.password) {
        errors.password = '请输入密码'
      } else if (form.password.length < 8) {
        errors.password = '密码至少8位'
      } else if (options.mode.value === 'register') {
        if (!/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password)) {
          errors.password = '密码需包含大小写字母'
        } else if (!/\d/.test(form.password)) {
          errors.password = '密码需包含数字'
        } else {
          errors.password = ''
        }
      } else {
        errors.password = ''
      }
    }

    if (field === 'confirmPassword' && options.mode.value === 'register') {
      if (!form.confirmPassword) {
        errors.confirmPassword = '请再次输入密码'
      } else if (form.password !== form.confirmPassword) {
        errors.confirmPassword = '两次密码不一致'
      } else {
        errors.confirmPassword = ''
      }
    }
  }

  function validateResetForm(): boolean {
    const { resetForm, resetErrors } = options
    let valid = true

    resetErrors.email = ''
    resetErrors.code = ''
    resetErrors.password = ''
    resetErrors.confirmPassword = ''

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetForm.email)) {
      resetErrors.email = '请输入有效的邮箱地址'
      valid = false
    }

    if (!resetForm.code || resetForm.code.length !== 6) {
      resetErrors.code = '请输入6位验证码'
      valid = false
    }

    if (resetForm.password.length < 8) {
      resetErrors.password = '密码至少8位'
      valid = false
    } else if (!/[a-z]/.test(resetForm.password) || !/[A-Z]/.test(resetForm.password)) {
      resetErrors.password = '密码需包含大小写字母'
      valid = false
    } else if (!/\d/.test(resetForm.password)) {
      resetErrors.password = '密码需包含数字'
      valid = false
    }

    if (resetForm.password !== resetForm.confirmPassword) {
      resetErrors.confirmPassword = '两次密码不一致'
      valid = false
    }

    return valid
  }

  function validateSubmitForm(): boolean {
    const { form, errors } = options
    let valid = true

    errors.email = ''
    errors.password = ''
    errors.confirmPassword = ''
    errors.code = ''

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) {
      errors.email = '请输入有效的邮箱地址'
      valid = false
    }

    if (options.mode.value === 'code') {
      if (!form.code || form.code.length !== 6) {
        errors.code = '请输入6位验证码'
        valid = false
      }
      return valid
    }

    if (form.password.length < 8) {
      errors.password = '密码至少8位'
      valid = false
    } else if (options.mode.value === 'register') {
      if (!/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password)) {
        errors.password = '密码需包含大小写字母'
        valid = false
      } else if (!/\d/.test(form.password)) {
        errors.password = '密码需包含数字'
        valid = false
      }
    }

    if (options.mode.value === 'register') {
      if (!form.code || form.code.length !== 6) {
        errors.code = '请输入6位验证码'
        valid = false
      }
      if (form.password !== form.confirmPassword) {
        errors.confirmPassword = '两次密码不一致'
        valid = false
      }
    }

    return valid
  }

  return {
    validateField,
    validateResetForm,
    validateSubmitForm,
  }
}
