"""Render a Markdown document to a paginated PDF using ReportLab."""
import argparse
import html
import re
import textwrap
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, Preformatted, Image, KeepTogether


ROOT = Path(__file__).resolve().parents[1]
WIDTH = A4[0] - 88
styles = {
    'body': ParagraphStyle('body', fontName='Helvetica', fontSize=9.4, leading=14, spaceAfter=7),
    'h1': ParagraphStyle('h1', fontName='Helvetica-Bold', fontSize=23, leading=29, textColor=colors.HexColor('#173c57'), spaceAfter=15),
    'h2': ParagraphStyle('h2', fontName='Helvetica-Bold', fontSize=13, leading=18, textColor=colors.HexColor('#173c57'), spaceBefore=15, spaceAfter=9, keepWithNext=True),
    'h3': ParagraphStyle('h3', fontName='Helvetica-Bold', fontSize=10.5, leading=15, spaceBefore=10, spaceAfter=6, keepWithNext=True),
    'cell': ParagraphStyle('cell', fontName='Helvetica', fontSize=8.0, leading=11, splitLongWords=True),
    'code': ParagraphStyle('code', fontName='Courier', fontSize=6.8, leading=9, backColor=colors.HexColor('#f2f5f7'), borderPadding=8, spaceBefore=6, spaceAfter=12),
}


def inline(text):
    text = html.escape(text)
    text = re.sub(r'\[([^\]]+)\]\(([^)]+)\)', lambda m: '<a color="#176280" href="' + m[2] + '">' + m[1] + '</a>' if m[2].startswith('https://') else m[1], text)
    text = re.sub(r'`([^`]+)`', r'<font name="Courier">\1</font>', text)
    text = re.sub(r'\*\*([^*]+)\*\*', r'<b>\1</b>', text)
    return re.sub(r'\*([^*]+)\*', r'<i>\1</i>', text)


def render(source, target):
    lines = source.read_text(encoding='utf-8').splitlines()
    flow = []
    i = 0
    while i < len(lines):
        line = lines[i]
        if not line.strip():
            i += 1
            continue
        if line.startswith('```'):
            i += 1
            code = []
            while i < len(lines) and not lines[i].startswith('```'):
                code.extend(textwrap.wrap(lines[i], width=116, replace_whitespace=False, drop_whitespace=False) or [''])
                i += 1
            # ASCII arrows avoid missing glyphs in the built-in monospace font.
            flow.append(KeepTogether([Preformatted('\n'.join(code).replace('→', '->').replace('×', 'x'), styles['code'])]))
            i += 1
            continue
        if line.startswith('|'):
            rows = []
            while i < len(lines) and lines[i].startswith('|'):
                cells = [v.strip() for v in lines[i].strip('|').split('|')]
                if not all(re.fullmatch(r':?-+:?', c) for c in cells):
                    rows.append(cells)
                i += 1
            counts = [max(len(row[c]) for row in rows) for c in range(len(rows[0]))]
            weights = [max(8, min(c, 45)) for c in counts]
            widths = [WIDTH * w / sum(weights) for w in weights]
            data = [[Paragraph(('<b>' + inline(c) + '</b>') if r == 0 else inline(c), styles['cell']) for c in row] for r, row in enumerate(rows)]
            table = Table(data, colWidths=widths, repeatRows=1, hAlign='LEFT')
            table.setStyle(TableStyle([
                ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor('#e2edf3')),
                ('ROWBACKGROUNDS', (0, 1), (-1, -1), [colors.white, colors.HexColor('#f6f8fa')]),
                ('VALIGN', (0, 0), (-1, -1), 'TOP'),
                ('LEFTPADDING', (0, 0), (-1, -1), 6), ('RIGHTPADDING', (0, 0), (-1, -1), 6),
                ('TOPPADDING', (0, 0), (-1, -1), 7), ('BOTTOMPADDING', (0, 0), (-1, -1), 7),
                ('LINEBELOW', (0, 0), (-1, 0), 0.6, colors.HexColor('#9ab3c3')),
            ]))
            flow.extend([KeepTogether([table, Spacer(1, 10)])])
            continue
        if line.startswith('#'):
            depth = len(line) - len(line.lstrip('#'))
            flow.append(Paragraph(inline(line[depth:].strip()), styles['h' + str(min(depth, 3))]))
            i += 1
            continue
        if line.startswith('- ') or re.match(r'\d+\. ', line):
            flow.append(Paragraph(inline(line), styles['body']))
            i += 1
            continue
        paragraph = [line]
        i += 1
        while i < len(lines) and lines[i].strip() and not lines[i].startswith(('#', '|', '```', '- ')) and not re.match(r'\d+\. ', lines[i]):
            paragraph.append(lines[i])
            i += 1
        flow.append(Paragraph(inline(' '.join(paragraph)), styles['body']))
        if 'El resultado es `AesCbc`.' in paragraph[0]:
            flow.append(KeepTogether([Image(str(ROOT / 'output/stegoanalysis/hugo4-LSB4.png'), width=309.75, height=226.5), Spacer(1, 10)]))
        if paragraph[0].startswith('Los fotogramas recuperados'):
            flow.append(KeepTogether([Image(str(ROOT / 'output/stegoanalysis/video-contact-sheet.jpg'), width=340, height=340 * 840 / 1280), Spacer(1, 10)]))

    def footer(canvas, doc):
        canvas.saveState()
        canvas.setStrokeColor(colors.HexColor('#c8d6df'))
        canvas.line(44, 38, A4[0] - 44, 38)
        canvas.setFont('Helvetica', 8)
        canvas.setFillColor(colors.HexColor('#526572'))
        canvas.drawString(44, 25, 'Criptografía y Seguridad | Grupo 3')
        canvas.drawRightString(A4[0] - 44, 25, str(doc.page))
        canvas.restoreState()

    target.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(str(target), pagesize=A4, rightMargin=44, leftMargin=44, topMargin=40, bottomMargin=52,
                            title=source.stem, author='Grupo 3')
    doc.build(flow, onFirstPage=footer, onLaterPages=footer)
    print(target)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, default=ROOT / 'docs/ARQUITECTURA.md')
    parser.add_argument('--output', type=Path, default=ROOT / 'output/pdf/Arquitectura.pdf')
    args = parser.parse_args()
    render(args.input, args.output)
