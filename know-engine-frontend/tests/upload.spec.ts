import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'

const file = { name: 'knowledge-notes.txt', mimeType: 'text/plain', buffer: Buffer.from('Knowledge begins here.') }
const document = {
  docId: 42, docTitle: file.name, uploadUser: 'test-user',
  accessibleBy: null, status: 'UPLOADED', docUrl: 'http://localhost:9000/documents/knowledge-notes.txt',
}

async function fillUpload(page: Page) {
  await page.getByLabel('选择文档文件').setInputFiles(file)
  await page.getByLabel('上传用户').fill('  test-user  ')
}

test('validates missing fields, empty files, oversized files and multiple dropped files', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  await expect(page.getByText('请先选择需要上传的文档。')).toBeVisible()
  await expect(page.getByText('请填写上传用户。')).toBeVisible()

  await page.getByLabel('选择文档文件').setInputFiles({ ...file, buffer: Buffer.alloc(0) })
  await expect(page.getByText('这个文件是空的，请选择有内容的文件。')).toBeVisible()
  await page.getByLabel('选择文档文件').setInputFiles({ ...file, buffer: Buffer.alloc(1024 * 1024 + 1) })
  await expect(page.getByText('文件过大，单个文件不能超过 1 MB。')).toBeVisible()

  const dataTransfer = await page.evaluateHandle(() => {
    const transfer = new DataTransfer()
    transfer.items.add(new File(['one'], 'one.txt', { type: 'text/plain' }))
    transfer.items.add(new File(['two'], 'two.txt', { type: 'text/plain' }))
    return transfer
  })
  await page.locator('.dropzone').dispatchEvent('drop', { dataTransfer })
  await expect(page.getByText('每次请选择一份文档，上传完成后可以继续添加。')).toBeVisible()
  await expect(page.locator('.record-row')).toHaveCount(0)
})

test('sends correct multipart fields, omits empty optional field and shows returned document', async ({ page }) => {
  let body = ''
  let contentType = ''
  await page.route('**/api/knowledge/documents/upload', async route => {
    body = route.request().postDataBuffer()?.toString() || ''
    contentType = route.request().headers()['content-type']
    expect(route.request().method()).toBe('POST')
    await route.fulfill({ json: document })
  })
  await page.goto('/')
  await fillUpload(page)
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('上传成功')
  expect(contentType).toMatch(/multipart\/form-data; boundary=/)
  expect(body).toContain('name="file"; filename="knowledge-notes.txt"')
  expect(body).toContain('name="uploadUser"\r\n\r\ntest-user')
  expect(body).not.toContain('name="accessibleBy"')
  await expect(page.locator('.record-row')).toHaveCount(1)
  await expect(page.locator('.record-row')).toContainText('已上传')
  await expect(page.locator('.selected-file')).toHaveCount(0)

  await page.getByRole('button', { name: '查看详情' }).click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(page.getByRole('dialog')).toContainText('42')
  await expect(page.getByRole('link', { name: '打开原始文档' })).toHaveAttribute('href', document.docUrl)
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).not.toBeVisible()
})

test('preserves file for retry, sends visibility unchanged, and rejects unsafe document links', async ({ page }) => {
  let calls = 0
  let body = ''
  await page.route('**/api/knowledge/documents/upload', async route => {
    calls += 1
    body = route.request().postDataBuffer()?.toString() || ''
    if (calls === 1) await route.fulfill({ status: 500, json: { message: 'internal error' } })
    else await route.fulfill({ json: { ...document, accessibleBy: 'team-alpha', docUrl: 'javascript:alert(1)' } })
  })
  await page.goto('/')
  await fillUpload(page)
  await page.getByLabel('可见范围').fill('  team-alpha  ')
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('服务器暂时无法保存文件')
  await expect(page.locator('.selected-file')).toContainText(file.name)
  await expect(page.locator('.record-row')).toHaveCount(0)
  await page.getByRole('button', { name: '重新上传', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('上传成功')
  expect(body).toContain('name="accessibleBy"\r\n\r\nteam-alpha')
  await page.getByRole('button', { name: '查看详情' }).click()
  await expect(page.getByRole('dialog')).toContainText('team-alpha')
  await expect(page.getByRole('link', { name: '打开原始文档' })).toHaveCount(0)
})

test('waits for server confirmation, prevents duplicate requests, and supports cancellation', async ({ page }) => {
  let requestCount = 0
  await page.route('**/api/knowledge/documents/upload', async route => {
    requestCount += 1
    await new Promise(resolve => setTimeout(resolve, 1500))
    await route.fulfill({ json: document }).catch(() => {})
  })
  await page.goto('/')
  await fillUpload(page)
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  await expect(page.getByRole('button', { name: '正在上传文档…' })).toBeDisabled()
  await expect(page.getByLabel('上传用户')).toBeDisabled()
  await expect(page.getByRole('progressbar')).toBeVisible()
  await expect(page.locator('.record-row')).toHaveCount(0)
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('已取消等待上传结果')
  await expect(page.locator('.record-row')).toHaveCount(0)
  await expect(page.getByRole('button', { name: '上传文档', exact: true })).toBeEnabled()
  expect(requestCount).toBe(1)
})

test('supports one-file drag and drop and handles disconnected service without fake success', async ({ page }) => {
  await page.route('**/api/knowledge/documents/upload', route => route.abort('connectionrefused'))
  await page.goto('/')
  const dataTransfer = await page.evaluateHandle(() => {
    const transfer = new DataTransfer()
    transfer.items.add(new File(['Useful notes'], 'dropped.txt', { type: 'text/plain' }))
    return transfer
  })
  await page.locator('.dropzone').dispatchEvent('drop', { dataTransfer })
  await expect(page.locator('.selected-file')).toContainText('dropped.txt')
  await page.getByLabel('上传用户').fill('test-user')
  await page.getByRole('button', { name: '上传文档', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('无法连接上传服务')
  await expect(page.locator('.record-row')).toHaveCount(0)
})

for (const width of [320, 390, 768, 1440]) {
  test(`fits the ${width}px viewport without horizontal overflow`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1100 })
    await page.goto('/')
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible()
    await expect(page.getByRole('button', { name: '选择文件', exact: true })).toBeVisible()
    const sizes = await page.evaluate(() => ({ document: document.documentElement.scrollWidth, viewport: window.innerWidth }))
    expect(sizes.document).toBeLessThanOrEqual(sizes.viewport)
    if (width === 1440 || width === 390) {
      await page.screenshot({ path: `test-results/upload-${width}.png`, fullPage: true })
    }
  })
}
